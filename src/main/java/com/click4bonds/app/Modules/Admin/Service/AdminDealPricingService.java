package com.click4bonds.app.Modules.Admin.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Admin.Dto.AdminDealPricingPreviewResponse;
import com.click4bonds.app.Modules.Admin.Dto.PreviewDealPricingRequest;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.PriceCalculationService;
import com.click4bonds.app.Modules.Bond.Service.YtmCalculationService;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Redis.RedisService;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealPricing;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Prices a bond for a deal that has not been created yet.
 *
 * <p>The two engines the platform already has answer "what does this bond yield
 * at this price?" and "what price yields this rate?". This class runs them for
 * an employee looking at a prospective deal and keeps the answer in Redis, keyed
 * to the employee and the bond. The bond's own stored price and yield are never
 * read as the input and never written — the figures belong to the deal the
 * employee is about to strike.</p>
 *
 * <p>A create request consults the cache and saves its figures on the new deal;
 * {@link AdminDealService#calculate} then edits the deal in the database, so the
 * cache only bridges the gap before the row exists.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDealPricingService {

    /**
     * How long a previewed price stays available to the employee's next create
     * request. Long enough to fill in the rest of the form, short enough that a
     * stale negotiation cannot be picked up hours later.
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final RedisService redisService;
    private final BondService bondService;
    private final PriceCalculationService priceCalculationService;
    private final YtmCalculationService ytmCalculationService;
    private final DocumentProperties documentProperties;

    /**
     * The cached figures.
     *
     * <p>A top-level value object rather than a nested one so the Redis mapper
     * binds it back without any type information in the payload. {@code valueDate}
     * travels with the figures because the price was measured to it.</p>
     */
    public record CachedPricing(
            BigDecimal pricePerUnit,
            BigDecimal annualYtm,
            BigDecimal semiYtm,
            LocalDate valueDate) {
    }

    /**
     * Calculates the missing half of a price/yield pair and caches both.
     *
     * @throws BadRequestException when neither or both figures are supplied, or
     *                             the supplied price is not positive
     */
    public AdminDealPricingPreviewResponse preview(
            UUID employeeId,
            PreviewDealPricingRequest request) {

        boolean pricedByPrice = request.price() != null;
        boolean pricedByYtm = request.annualYtm() != null;

        if (pricedByPrice == pricedByYtm) {
            throw new BadRequestException(
                    "Supply exactly one of price or annualYtm");
        }

        if (pricedByPrice && request.price().signum() <= 0) {
            throw new BadRequestException("Price must be greater than 0");
        }

        Bond bond = bondService.findBondById(request.bondId());

        LocalDate valueDate = (request.dealDate() == null
                ? LocalDate.now()
                : request.dealDate())
                .plusDays(documentProperties.getDeal().getValueDateOffsetDays());

        BigDecimal pricePerUnit;
        BigDecimal annualYtm;

        if (pricedByPrice) {

            pricePerUnit = request.price();
            annualYtm = ytmCalculationService
                    .previewAnnualYtm(bond, valueDate, pricePerUnit);

        } else {

            annualYtm = request.annualYtm();
            pricePerUnit = priceCalculationService
                    .calculatePrice(bond, valueDate, annualYtm.movePointLeft(2))
                    .price();
        }

        BigDecimal semiYtm = YtmCalculationService.semiYtmPercentage(annualYtm);

        redisService.set(
                key(employeeId, bond.getId()),
                new CachedPricing(pricePerUnit, annualYtm, semiYtm, valueDate),
                CACHE_TTL);

        log.info(
                "Deal pricing previewed: employee={} isin={} price={} annualYtm={} (valueDate={})",
                employeeId,
                bond.getIsin(),
                pricePerUnit,
                annualYtm,
                valueDate);

        return new AdminDealPricingPreviewResponse(
                bond.getId(),
                bond.getIsin(),
                bond.getName(),
                valueDate,
                pricePerUnit,
                annualYtm,
                semiYtm);
    }

    /**
     * The cached figures for this employee and bond, if a preview is still
     * live.
     */
    public Optional<DealPricing> cachedPricing(UUID employeeId, UUID bondId) {

        return redisService
                .get(key(employeeId, bondId), CachedPricing.class)
                .map(cached -> new DealPricing(
                        cached.pricePerUnit(),
                        cached.annualYtm(),
                        cached.semiYtm()));
    }

    /** Forgets a preview once it has been saved on a deal. */
    public void clear(UUID employeeId, UUID bondId) {
        redisService.delete(key(employeeId, bondId));
    }

    private static String key(UUID employeeId, UUID bondId) {
        return "admin:deal-pricing:" + employeeId + ":" + bondId;
    }
}
