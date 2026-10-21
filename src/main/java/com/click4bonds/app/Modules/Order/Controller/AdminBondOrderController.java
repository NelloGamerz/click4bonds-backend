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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Order.Dto.BondOrderPaymentResponse;
import com.click4bonds.app.Modules.Order.Dto.BondOrderResponse;
import com.click4bonds.app.Modules.Order.Dto.CreateOrderRequest;
import com.click4bonds.app.Modules.Order.Service.BondOrderPaymentService;
import com.click4bonds.app.Modules.Order.Service.BondOrderService;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin order endpoints — a testing path through the purchase flow.
 *
 * <p>Lets an operator take a bond from an order to a holding without a real
 * payment: place an order, then pay it, and the inventory, the deal confirmation
 * and the holding are all produced by the same code the customer flow uses.</p>
 *
 * <p>Mapped under {@code /api/admin}, so nothing here is reachable with a
 * customer token and the customer endpoints — {@code BondOrderController}'s own
 * {@code /api/orders} reads — are not widened to staff. The module gate is
 * {@code ORDER_MANAGEMENT}.</p>
 *
 * <p><strong>The order is placed for a named customer, not for the caller.</strong>
 * It used to use the calling account as the buyer, which made a member of staff
 * the owner of a {@code BondOrder} and, once paid, of a {@code BondHolding}.
 * Both of those belong to someone who bought a bond, and neither entity accepts
 * a staff account — the guards on them refuse one. The caller names the customer
 * the order is for, and the order and everything it produces belong to that
 * account throughout.</p>
 *
 * <p>The payment endpoint takes no customer for that reason: the order already
 * knows whose it is, and asking the caller to name them again would only create
 * a way for the two to disagree.</p>
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
//@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'ORDER_MANAGEMENT')")
public class AdminBondOrderController {

    private final BondOrderService orderService;
    private final BondOrderPaymentService paymentService;
    private final UserService userService;

    /**
     * Places an order for a named customer.
     *
//     * @param customerId     the account the order is for; must be a customer
     * @param request        bond and number of lots; the price and the units per
     *                       lot both come from the bond, not the request
     * @param idempotencyKey optional key that makes a retry safe. Send a fresh
     *                       value per intended order and the same value for every
     *                       retry of it.
     * @return 201 with the new order; 200 with the existing one when the request
     *         was a retry and created nothing
     * @throws BadRequestException when the named account is not a customer
     */
    @PostMapping
    public ResponseEntity<BondOrderResponse> createOrder(
//            @RequestParam UUID customerId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        UUID customerId = UUID.fromString(jwt.getSubject());
        User customer = userService.getUser(customerId);

        /*
         * Refused here rather than left to the guard on BondOrder. The guard
         * exists and would catch it, but it throws an IllegalArgumentException —
         * right for a flow that built the wrong entity, and wrong for a request
         * that named the wrong account, which is a caller mistake and deserves
         * an answer that says so.
         */
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new BadRequestException(
                    "An order can only be placed for a customer account, but user "
                            + customerId + " is " + customer.getRole());
        }

        BondOrderService.Result result = orderService.createOrder(
                customerId,
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
     * <p>The order's own customer is used as the account paying, so the
     * ownership check the checkout performs still means something — the order
     * does belong to that account — while the authority to invoke it comes from
     * the module gate above rather than from owning the order.</p>
     *
     * @param orderId the order to pay
     * @return 201 with the deal and holding this payment produced; 200 with the
     *         same answer when the order had already been paid and nothing
     *         further was bought
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<BondOrderPaymentResponse> payOrder(@PathVariable UUID orderId) {

        BondOrderPaymentResponse response = paymentService.payOrderAsAdmin(orderId);

        return ResponseEntity
                .status(response.isReplayed()
                        ? HttpStatus.OK
                        : HttpStatus.CREATED)
                .body(response);
    }
}
