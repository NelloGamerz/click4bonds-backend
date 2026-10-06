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

import com.click4bonds.app.Modules.Auth.Service.AuthJwtService;
import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.IssuerService;
import com.click4bonds.app.Modules.User.Enums.UserRole;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RestController
@RequestMapping("/api/bonds")
@RequiredArgsConstructor
@Slf4j
public class BondController {

    private final BondService bondService;
    private final IssuerService issuerService;
    private final AuthJwtService authJwtService;

    @GetMapping
    public ResponseEntity<Page<BondResponse>> getBonds(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isFlashNews,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = jwt == null ? null : UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                bondService.getBonds(
                        search,
                        isFlashNews,
                        pageable,
                        userId,
                        authJwtService.hasRole(jwt, UserRole.CUSTOMER)));
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
}
