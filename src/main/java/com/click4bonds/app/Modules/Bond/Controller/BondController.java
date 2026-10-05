package com.click4bonds.app.Modules.Bond.Controller;

import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Service.BondCashFlowService;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.IssuerService;

import lombok.RequiredArgsConstructor;

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

    @GetMapping
    public ResponseEntity<Page<BondResponse>> getBonds(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isFlashNews,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = jwt == null ? null : UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                bondService.getBonds(search, isFlashNews, pageable, userId));
    }

//    @GetMapping("/{isin}")
//    public ResponseEntity<BondResponse> getBond(
//            @PathVariable String isin,
//            @AuthenticationPrincipal Jwt jwt) {
//
//        UUID userId = jwt != null
//                    ? UUID.fromString(jwt.getSubject()): null;
//
//        return ResponseEntity.ok(
//                bondService.getBond(isin, userId));
//    }

    @GetMapping("/{isin}")
    public ResponseEntity<BondResponse> getBond(
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

        log.info(
                "Calling BondService.getBond: isin={}, userId={}",
                isin,
                userId
        );

        return ResponseEntity.ok(
                bondService.getBond(isin, userId)
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
                issuerService.getIssuerByIsin(isin, userId));
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
     * @return 404 when no bond carries that ISIN.
     */
    @GetMapping("/{isin}/cashflow")
    public ResponseEntity<BondCashFlowResponse> getBondCashFlow(
            @PathVariable String isin,
            @RequestParam(required = false) LocalDate calculationDate) {

        LocalDate asOf = calculationDate != null
                ? calculationDate
                : LocalDate.now();

        log.info(
                "GET /bonds/{}/cashflow - calculationDate={}",
                isin,
                asOf
        );

        return ResponseEntity.ok(
                bondCashFlowService.generateSchedule(
                        bondService.findBond(isin),
                        asOf
                )
        );
    }
}
