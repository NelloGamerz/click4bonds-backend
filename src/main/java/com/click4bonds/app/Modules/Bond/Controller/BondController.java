package com.click4bonds.app.Modules.Bond.Controller;

import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Auth.Service.AuthJwtService;
import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondDetailResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.BondCashFlowService;
import com.click4bonds.app.Modules.Bond.Service.BondCashFlowPdfService;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.IssuerService;
import com.click4bonds.app.Modules.User.Enums.UserRole;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/bonds")
@RequiredArgsConstructor
@Slf4j
public class BondController {

    private final BondService bondService;
    private final IssuerService issuerService;
    private final BondCashFlowService bondCashFlowService;
    private final BondCashFlowPdfService bondCashFlowPdfService;
    private final AuthJwtService authJwtService;

    /**
     * The public bond catalogue.
     *
     * <p>Beyond the free-text {@code search} and the {@code isFlashNews} flag,
     * the listing can be narrowed by facet. Each is optional and absent means
     * "no opinion", not "match nothing":</p>
     *
     * <ul>
     *   <li>{@code securityType} — {@code SECURED} or {@code UNSECURED}.</li>
     *   <li>{@code couponFrequency} — one of {@code MONTHLY},
     *       {@code QUARTERLY}, {@code HALF_YEARLY}, {@code YEARLY} or
     *       {@code AT_MATURITY}.</li>
     *   <li>{@code rating} — the credit grade, e.g. {@code AAA}. This matches
     *       the stored grade column, not the agency-and-grade string
     *       {@link BondResponse#getRating()} shows, so a row that displays
     *       {@code "CRISIL AAA"} is selected with {@code ?rating=AAA}. The
     *       comparison is case-insensitive; blank is treated as absent.</li>
     * </ul>
     *
     * <p>A value outside an enum's constants is a 400, not a silent no-match,
     * because the parameter is bound to the enum type directly. Facets combine
     * with AND, and each is applied inside the query so the returned
     * {@code Page} counts the filtered set.</p>
     */
    @GetMapping
    public ResponseEntity<Page<BondResponse>> getBonds(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isFlashNews,
            @RequestParam(required = false) SecurityType securityType,
            @RequestParam(required = false) CouponFrequency couponFrequency,
            @RequestParam(required = false) String rating,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = jwt == null ? null : UUID.fromString(jwt.getSubject());

        /*
         * The public catalogue lists ACTIVE bonds only — an inactive bond is
         * one the admin has not released, or has pulled, so it is not the
         * public's to see. The filter is applied inside the query rather than
         * to the finished page, so totalElements and the page count describe
         * the active set too; the admin listing at /api/admin/bonds keeps
         * showing every bond, which is what an admin needs to manage them. The
         * facet filters ride along the same way, and the admin listing does not
         * offer them at all.
         */
        return ResponseEntity.ok(
                bondService.getActiveBonds(
                        search,
                        isFlashNews,
                        securityType,
                        couponFrequency,
                        rating,
                        pageable,
                        userId,
                        authJwtService.hasRole(jwt, UserRole.CUSTOMER)));
    }

    @GetMapping("/{isin}")
    public ResponseEntity<BondDetailResponse> getBond(
            @PathVariable String isin,
            @AuthenticationPrincipal Jwt jwt) {

        log.info(
                "GET /bonds/{} - JWT present={}",
                isin,
                jwt != null
        );

        if (jwt != null) {

            log.info(
                    "JWT received: subject={}, issuer={}, claims={}",
                    jwt.getSubject(),
                    jwt.getIssuer(),
                    jwt.getClaims().keySet()
            );
        } else {

            log.warn(
                    "No JWT found for GET /bonds/{} - userId will be null",
                    isin
            );
        }

        UUID userId = null;

        if (jwt != null && jwt.getSubject() != null) {

            try {

                userId = UUID.fromString(jwt.getSubject());

                log.info(
                        "Resolved authenticated userId={} from JWT subject",
                        userId
                );

            } catch (IllegalArgumentException e) {

                log.error(
                        "JWT subject is not a valid UUID: subject={}",
                        jwt.getSubject(),
                        e
                );
            }
        }

        // The role travels in the token, so the customer check needs no
        // database round trip. A token with no role claim — or one this
        // application does not define — is not a customer.
        boolean isCustomer = authJwtService.hasRole(jwt, UserRole.CUSTOMER);

        log.info(
                "Calling BondService.getBond: isin={}, userId={}, isCustomer={}",
                isin,
                userId,
                isCustomer
        );

        return ResponseEntity.ok(
                bondService.getBond(isin, userId, isCustomer)
        );
    }


    // @PostMapping("/{bondId}/calculate-ytm")
    // public YtmResponse calculateYtm(
    // @PathVariable UUID bondId) {
    // return bondYtmService.calculateYtm(bondId);
    // }

    @GetMapping("/{isin}/issuer")
    public ResponseEntity<IssuerResponse> getIssuerByIsin(
            @PathVariable String isin,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(
                issuerService.getIssuerByIsin(
                        isin,
                        userId,
                        authJwtService.hasRole(jwt, UserRole.CUSTOMER)));
    }

    /**
     * Projects the bond's cash flow from {@code calculationDate} to maturity.
     *
     * <p>
     * The schedule lists the dated coupon and principal movements, plus the
     * purchase leg when the bond has a usable price. It is the same series the
     * published YTM is discounted from, so the two always reconcile.
     *
     * @param isin            the bond's ISIN, case-insensitive.
     * @param calculationDate ISO date the projection starts from; defaults to
     *                        today. An amortizing bond bought later therefore
     *                        reports fewer remaining cash flows.
     * @param totalBond       how many bonds to project for; defaults to 1. Every
     *                        amount is per single bond and is scaled by this, so
     *                        {@code totalBond=5} returns the schedule for five.
     * @param download        {@code ?download=true} answers with the statement as
     *                        a PDF attachment instead of JSON. The figures are the
     *                        same either way; only the representation changes.
     *                        Absent or false leaves the response exactly as it was.
     * @return 404 when no bond carries that ISIN. With {@code download=false} the
     *         projected schedule; with {@code download=true} that same projection
     *         as a PDF.
     */
    @GetMapping("/{isin}/cashflow")
    public ResponseEntity<?> getBondCashFlow(
            @PathVariable String isin,
            @RequestParam(required = false) LocalDate calculationDate,
            @RequestParam(required = false) BigDecimal totalBond,
            @RequestParam(required = false, defaultValue = "false") boolean download) {

        LocalDate asOf = calculationDate != null
                ? calculationDate
                : LocalDate.now();

        BigDecimal quantity = totalBond != null
                ? totalBond
                : BigDecimal.ONE;

        log.info(
                "GET /bonds/{}/cashflow - calculationDate={} totalBond={} download={}",
                isin,
                asOf,
                quantity,
                download
        );

        Bond bond = bondService.findBond(isin);

        BondCashFlowResponse cashFlow = bondCashFlowService.generateSchedule(
                bond,
                asOf,
                quantity
        );

        if (!download) {
            return ResponseEntity.ok(cashFlow);
        }

        /*
         * Sent as an attachment so the browser saves the statement rather than
         * trying to render it in a tab, and named after the ISIN so two
         * downloads do not collide.
         */
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(bondCashFlowPdfService.fileName(bond))
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bondCashFlowPdfService.render(bond, cashFlow));
    }
}
