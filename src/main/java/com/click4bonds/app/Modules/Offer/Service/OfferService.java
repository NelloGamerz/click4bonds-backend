package com.click4bonds.app.Modules.Offer.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Admin.Service.AuditLogService;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.PriceCalculationService;
import com.click4bonds.app.Modules.Bond.Service.YtmCalculationService;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Offer.Dto.CreateOfferRequest;
import com.click4bonds.app.Modules.Offer.Dto.OfferDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves one bond offer and asks a design to draw it.
 *
 * <p>
 * The caller may send one half of the price/yield pair; the other is derived with
 * the platform's existing engines — {@link PriceCalculationService} for yield to
 * price and {@link YtmCalculationService#previewAnnualYtm(Bond, LocalDate, BigDecimal)}
 * for price to yield. Both are read-only, so an offer never moves the bond's
 * quoted price or yield. When the caller sends neither, the offer uses the price
 * and yield already stored on the bond.
 *
 * <p>
 * Nothing is stored: no offer row, no rendered PDF. The document is returned to
 * the caller and forgotten.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfferService {

    /** A yield is quoted in percent; the pricing engine works in decimals. */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BondService bondService;
    private final PriceCalculationService priceCalculationService;
    private final YtmCalculationService ytmCalculationService;
    private final OfferDesignStrategyFactory designFactory;
    private final AuditLogService auditLogService;

    /**
     * Draws the offer as a PDF.
     *
     * @param request ISIN plus at most one of YTM or price, and an optional design
     * @return the rendered PDF
     * @throws BadRequestException when both YTM and price are sent, when the design
     *                             is unknown, or when the pricing engine refuses the
     *                             figure
     * @throws com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException
     *         when no bond carries the ISIN
     */
    public byte[] renderOffer(CreateOfferRequest request) {

        boolean hasYtm = request.ytm() != null;
        boolean hasPrice = request.price() != null;

        if (hasYtm && hasPrice) {
            throw new BadRequestException(
                    "Send at most one of 'ytm' (percentage) or 'price' (percentage of par)");
        }

        Bond bond = bondService.findBond(request.isin());

        LocalDate asOf = LocalDate.now();

        BigDecimal ytmPercentage;
        BigDecimal price;

        if (hasYtm) {

            ytmPercentage = request.ytm();
            price = priceCalculationService
                    .calculatePrice(bond, asOf, ytmPercentage.divide(HUNDRED))
                    .price();

        } else if (hasPrice) {

            price = request.price();
            ytmPercentage = ytmCalculationService
                    .previewAnnualYtm(bond, asOf, price);

        } else {

            /*
             * Neither figure was sent, so the offer is drawn from the bond's own
             * stored price and yield. Nothing is calculated and nothing is written.
             */
            price = bond.getPrice();
            ytmPercentage = bond.getAnnualYtm();
        }

        OfferDetails offer = new OfferDetails(
                bond.getIsin(),
                bond.getName(),
                humanise(bond.getSecurityType() == null
                        ? null
                        : bond.getSecurityType().name()),
                combineRating(bond.getRatingAgency(), bond.getRating()),
                bond.getCouponRate(),
                humanise(bond.getCouponFrequency() == null
                        ? null
                        : bond.getCouponFrequency().name()),
                bond.getMaturityDate(),
                bond.getPutCallDescription(),
                price,
                ytmPercentage,
                asOf);

        log.info(
                "Rendering offer: isin={} price={} ytm={}% design={}",
                offer.isin(),
                offer.price(),
                offer.ytmPercentage(),
                request.design());

        byte[] pdf = designFactory
                .strategyFor(request.design())
                .render(offer);

        auditLogService.record(
                "CREATE_OFFER",
                "BOND",
                null,
                "Rendered offer PDF for ISIN " + bond.getIsin());

        return pdf;
    }

    /**
     * Joins the agency and grade into one string, e.g. {@code CRISIL AAA}.
     * Returns null when the bond has neither, rather than an empty string.
     */
    private static String combineRating(String agency, String rating) {

        boolean hasAgency = agency != null && !agency.isBlank();
        boolean hasRating = rating != null && !rating.isBlank();

        if (hasAgency && hasRating) {
            return agency.trim() + " " + rating.trim();
        }

        if (hasAgency) {
            return agency.trim();
        }

        return hasRating ? rating.trim() : null;
    }

    /**
     * Turns an enum constant into printable text: {@code HALF_YEARLY} becomes
     * {@code Half yearly}.
     */
    private static String humanise(String constant) {

        if (constant == null) {
            return null;
        }

        String words = constant.replace('_', ' ').toLowerCase(Locale.ENGLISH);

        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
