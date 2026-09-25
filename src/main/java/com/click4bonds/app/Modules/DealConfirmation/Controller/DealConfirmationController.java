package com.click4bonds.app.Modules.DealConfirmation.Controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Deal confirmation API.
 *
 * <p>Follows {@code BondOrderController}: the caller is the subject of the
 * verified JWT, never a field in the body, so a customer cannot buy in someone
 * else's name, and the buyer is looked up from that subject rather than trusted
 * from the request.</p>
 *
 * <p>The {@code @PreAuthorize} mirrors the role rule the rest of the application
 * declares. It only takes effect if method security is switched on
 * ({@code @EnableMethodSecurity}); today the request is authenticated by the
 * {@code SecurityConfig} filter chain regardless, and the customer must be an
 * active account for the deal to be accepted.</p>
 */
@RestController
@RequestMapping("/api/deal-confirmations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class DealConfirmationController {

    private final DealConfirmationService dealConfirmationService;

    /**
     * Buys a bond.
     *
     * @param request        bond and quantities; the total is calculated server side
     * @param idempotencyKey optional key that makes a retry safe. A client that
     *                       may retry (double click, flaky mobile connection)
     *                       should send a fresh value per intended purchase and
     *                       the same value for every retry of it.
     * @param jwt            verified token; its subject identifies the customer
     * @return 201 with the new deal, or 200 with the existing one when the
     *         request was a retry and bought nothing further
     */
    @PostMapping
    public ResponseEntity<DealConfirmationResponse> createDealConfirmation(
            @Valid @RequestBody CreateDealConfirmationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        DealConfirmationService.Result result = dealConfirmationService.createDeal(
                UUID.fromString(jwt.getSubject()),
                request,
                idempotencyKey);

        /*
         * A replayed request is answered 200 rather than 201: nothing was
         * created this time, and a client that treats 201 as "a new purchase
         * happened" would be misled.
         */
        return ResponseEntity
                .status(result.replayed()
                        ? HttpStatus.OK
                        : HttpStatus.CREATED)
                .body(result.response());
    }
}
