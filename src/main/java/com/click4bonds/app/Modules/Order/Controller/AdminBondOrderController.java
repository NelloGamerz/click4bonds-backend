package com.click4bonds.app.Modules.Order.Controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Order.Dto.BondOrderPaymentResponse;
import com.click4bonds.app.Modules.Order.Dto.BondOrderResponse;
import com.click4bonds.app.Modules.Order.Dto.CreateOrderRequest;
import com.click4bonds.app.Modules.Order.Service.BondOrderPaymentService;
import com.click4bonds.app.Modules.Order.Service.BondOrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin order endpoints — a testing path through the purchase flow.
 *
 * <p>Lets an admin take a bond from an order to a holding without a real
 * customer or a real payment: place an order, then pay it, and the inventory,
 * the deal confirmation and the holding are all produced by the same code the
 * customer flow uses.</p>
 *
 * <p>Mapped under {@code /api/admin} and declared {@code ROLE_ADMIN}, following
 * {@code AdminBondController}. {@code BondOrderController} — the customer's own
 * {@code /api/orders} reads — is untouched, so nothing here is reachable with a
 * customer token, and the customer endpoints are not widened to admins.</p>
 *
 * <p>The buyer is always the subject of the JWT, never a field in the body, so
 * an order is placed for the calling account and no one can create one in
 * another account's name.</p>
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBondOrderController {

    private final BondOrderService orderService;
    private final BondOrderPaymentService paymentService;

    /**
     * Places an order for the calling admin account.
     *
     * @param request        bond and number of lots; the price and the units per
     *                       lot both come from the bond, not the request
     * @param idempotencyKey optional key that makes a retry safe. Send a fresh
     *                       value per intended order and the same value for every
     *                       retry of it.
     * @param jwt            verified token; its subject is the buyer
     * @return 201 with the new order; 200 with the existing one when the request
     *         was a retry and created nothing
     */
    @PostMapping
    public ResponseEntity<BondOrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        UUID adminId = UUID.fromString(jwt.getSubject());

        BondOrderService.Result result = orderService.createOrder(
                adminId,
                request,
                idempotencyKey);

        /*
         * A replayed request is answered 200 rather than 201: nothing was created
         * this time, and a client that treats 201 as "a new order happened" would
         * be misled.
         */
        return ResponseEntity
                .status(result.replayed()
                        ? HttpStatus.OK
                        : HttpStatus.CREATED)
                .body(result.response());
    }

    /**
     * Runs the dummy payment and completes the purchase.
     *
     * <p>One transaction: the dummy payment, the deal confirmation, the inventory
     * reservation, the order's move to {@code CONFIRMED} and the credit to the
     * holding either all happen or none do.</p>
     *
     * @param orderId the order to pay
     * @param jwt     verified token; its subject must own the order
     * @return 201 with the deal and holding this payment produced; 200 with the
     *         same answer when the order had already been paid and nothing
     *         further was bought
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<BondOrderPaymentResponse> payOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID adminId = UUID.fromString(jwt.getSubject());

        BondOrderPaymentResponse response = paymentService.payOrder(adminId, orderId);

        return ResponseEntity
                .status(response.isReplayed()
                        ? HttpStatus.OK
                        : HttpStatus.CREATED)
                .body(response);
    }
}
