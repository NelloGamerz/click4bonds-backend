package com.click4bonds.app.Modules.Order.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;
import com.click4bonds.app.Modules.Bond.Enums.BondOrderStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationMapper;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationWriter;
import com.click4bonds.app.Modules.Holding.Model.BondHolding;
import com.click4bonds.app.Modules.Holding.Repository.BondHoldingRepository;
import com.click4bonds.app.Modules.Holding.Service.BondHoldingService;
import com.click4bonds.app.Modules.Order.Dto.BondOrderPaymentResponse;
import com.click4bonds.app.Modules.Order.Model.BondOrder;
import com.click4bonds.app.Modules.Order.Repository.BondOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pays for an order: dummy payment, deal confirmation, inventory and holding —
 * in one transaction.
 *
 * <p>This is the transactional core of the admin purchase flow, kept in its own
 * bean for the same reason {@code DealConfirmationWriter} is separate from
 * {@code DealConfirmationService}: Spring's {@code @Transactional} is applied by
 * a proxy, so a method calling another method of its own class bypasses it
 * silently. Everything written here must share one transaction, and the caller
 * that has to react to a duplicate request must be outside it.</p>
 *
 * <h2>What one payment does</h2>
 *
 * <ol>
 *   <li>Refuses anything but a {@code PENDING} order, so an order cannot be paid
 *       twice.</li>
 *   <li>Runs the dummy payment.</li>
 *   <li>Delegates to {@link DealConfirmationWriter#create}. That call reserves
 *       the bond's inventory — a single conditional {@code UPDATE} that is the
 *       oversell guard — and writes the deal confirmation. Because it is
 *       {@code REQUIRED}, it <em>joins</em> this transaction rather than
 *       starting one, so the reservation is undone if any later step fails.</li>
 *   <li>Moves the order to {@code CONFIRMED}.</li>
 *   <li>Adds the units to the buyer's holding.</li>
 * </ol>
 *
 * <p><strong>Inventory is not decremented here.</strong> The decrement belongs
 * to the writer's conditional {@code UPDATE}, which is evaluated by PostgreSQL
 * while it holds the row lock and therefore cannot oversell. Adding a second
 * decrement — an optimistic read-check-then-write, say — would either double
 * count or reopen the race the guard exists to close.</p>
 *
 * <h2>Idempotency</h2>
 *
 * <p>The order's status is the guard: an order already at {@code CONFIRMED} is
 * replayed rather than paid again, and adds nothing to the holding. The deal is
 * created under the derived key {@code ORDER-<orderId>}, so the existing unique
 * index on {@code (customer_id, idempotency_key)} is the backstop for two
 * payments that arrive at the same instant — see
 * {@link BondOrderPaymentService}, which catches that collision and answers with
 * the deal that won.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BondOrderCheckoutService {

    /**
     * Prefix for the deal's idempotency key. Distinct from any client-supplied
     * key so a customer's own key can never collide with an order-derived one.
     */
    private static final String DEAL_KEY_PREFIX = "ORDER-";

    private final BondOrderRepository orderRepository;
    private final DealConfirmationWriter dealConfirmationWriter;
    private final DealConfirmationRepository dealConfirmationRepository;
    private final DealConfirmationMapper dealConfirmationMapper;
    private final BondHoldingRepository bondHoldingRepository;
    private final BondHoldingService bondHoldingService;
    private final DummyPaymentProcessor dummyPaymentProcessor;
    private final AnalyticsService analyticsService;

    /**
     * What one checkout produced.
     *
     * <p>The document snapshot travels out of the transaction rather than being
     * acted on inside it. Asking for the deal's letter while the transaction is
     * still open would publish a request for a deal that could then roll back,
     * and the letter would be produced for a deal that does not exist. The
     * caller publishes it once this transaction has committed — the same split
     * {@code DealConfirmationService} makes.</p>
     *
     * @param response     what the caller gets back
     * @param dealId       the deal to letter, or null when nothing was created
     * @param documentData the snapshot the letter is built from, or null when
     *                     nothing was created
     */
    public record CheckoutResult(
            BondOrderPaymentResponse response,
            UUID dealId,
            DealConfirmationDocumentData documentData) {

        /** True when this checkout wrote nothing and only replayed a payment. */
        public boolean hasDocumentToProduce() {
            return dealId != null && documentData != null;
        }
    }

    /**
     * Pays for an order, or replays a payment already made against it.
     *
     * @param callerId the account paying, from the JWT; must own the order
     * @param orderId  the order to pay
     * @return the order's new state, the deal it produced and the holding it
     *         landed in — plus the snapshot for the deal's letter, which the
     *         caller produces once this transaction has committed
     * @throws ResourceNotFoundException no such order
     * @throws ForbiddenException        the order belongs to another account
     * @throws BadRequestException       the order is not payable (already
     *                                   cancelled, failed or refunded), or the
     *                                   bond has no usable lot size
     * @throws com.click4bonds.app.Modules.Common.Exceptions.ConflictException
     *                                   the bond does not have enough units left
     */
    @Transactional
    public CheckoutResult checkout(UUID callerId, UUID orderId) {

        BondOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderId));

        UUID ownerId = order.getCustomer().getId();

        /*
         * Checked before the status so another account's order is "forbidden"
         * rather than "already paid" or "not payable", which would tell a caller
         * something about an order that is not theirs.
         */
        if (!ownerId.equals(callerId)) {
            throw new ForbiddenException("You cannot pay for this order");
        }

        if (order.getStatus() == BondOrderStatus.PAID
                || order.getStatus() == BondOrderStatus.CONFIRMED) {

            /*
             * No document is asked for on a replay: the payment that did the work
             * already asked for it, and asking again would render and store a
             * second copy of a letter that exists.
             */
            return new CheckoutResult(replay(order), null, null);
        }

        if (order.getStatus() != BondOrderStatus.PENDING) {

            throw new BadRequestException(
                    "Order cannot be paid in status " + order.getStatus()
                            + ": " + order.getOrderNumber());
        }

        Bond bond = order.getBond();
        long quantity = order.getQuantity();
        BigDecimal pricePerUnit = order.getPricePerUnit();

        String paymentReference = dummyPaymentProcessor.process(order.getOrderNumber());

        analyticsService.track(
                AnalyticsEventType.PAYMENT_INITIATED,
                ownerId,
                null,
                bond.getId(),
                "WEB",
                "ORDER",
                java.util.Map.of(
                        "orderId", order.getId().toString(),
                        "amount", order.getTotalAmount()));

        long numberOfLots = resolveNumberOfLots(bond, quantity);

        /*
         * Reserves the inventory and writes the deal. Both happen inside this
         * transaction, so a failure below rolls the reservation back.
         */
        DealConfirmationWriter.CreatedDeal created = dealConfirmationWriter.create(
                callerId,
                new CreateDealConfirmationRequest(bond.getIsin(), numberOfLots, null, null),
                DEAL_KEY_PREFIX + order.getId());

        DealConfirmationResponse deal = created.response();

        /*
         * The reservation inside the writer is a bulk UPDATE configured to clear
         * the persistence context, which detaches `order` and everything hanging
         * off it. Reload it managed before changing it, and take the bond and
         * customer from the managed instance — reading the lazy associations off
         * the detached entity would fail.
         */
        BondOrder paidOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderId));

        paidOrder.setStatus(BondOrderStatus.CONFIRMED);

        BondOrder savedOrder = orderRepository.save(paidOrder);

        BondHolding holding = bondHoldingService.recordPurchase(
                savedOrder.getCustomer(),
                savedOrder.getBond(),
                quantity,
                pricePerUnit);

        log.info(
                "Order paid: orderNumber={} dealReference={} quantity={} holdingQuantity={}",
                savedOrder.getOrderNumber(),
                deal.getDealReference(),
                quantity,
                holding.getQuantity());

        java.util.Map<String, Object> completed = java.util.Map.of(
                "orderId", savedOrder.getId().toString(),
                "dealReference", deal.getDealReference(),
                "quantity", quantity);

        analyticsService.track(
                AnalyticsEventType.PAYMENT_COMPLETED,
                ownerId,
                null,
                bond.getId(),
                "WEB",
                "ORDER",
                completed);

        analyticsService.track(
                AnalyticsEventType.ORDER_CONFIRM,
                ownerId,
                null,
                bond.getId(),
                "WEB",
                "ORDER",
                completed);

        /*
         * The snapshot for the letter is mapped here, inside the transaction and
         * while the session is open, because the caller that produces the letter
         * runs after this transaction has closed and may not touch a lazy
         * association — the same reason DealConfirmationWriter hands its document
         * data out rather than a deal id to look up.
         */
        return new CheckoutResult(
                buildResponse(savedOrder, deal, holding, paymentReference, false),
                deal.getDealConfirmationId(),
                created.documentData());
    }

    /**
     * Answers a payment that has already been made.
     *
     * <p>Reads only: the deal and the holding are looked up and reported, and
     * nothing is written. The deal is found through the same derived key
     * {@link #checkout} created it under, so this cannot pick up a deal from
     * some other flow.</p>
     */
    private BondOrderPaymentResponse replay(BondOrder order) {

        UUID customerId = order.getCustomer().getId();
        UUID bondId = order.getBond().getId();

        DealConfirmationResponse deal = dealConfirmationRepository
                .findByCustomer_IdAndIdempotencyKey(
                        customerId,
                        DEAL_KEY_PREFIX + order.getId())
                .map(dealConfirmationMapper::toResponse)
                .orElse(null);

        BondHolding holding = bondHoldingRepository
                .findByCustomerIdAndBondId(customerId, bondId)
                .orElse(null);

        log.info(
                "Order already paid; replaying: orderNumber={} dealReference={} status={}",
                order.getOrderNumber(),
                deal == null ? null : deal.getDealReference(),
                order.getStatus());

        return buildResponse(order, deal, holding, null, true);
    }

    /**
     * Converts the order's quantity into the number of lots the deal flow
     * expects.
     *
     * <p>{@code DealConfirmationWriter} derives the units from the bond, so it
     * takes a lot count rather than a quantity. The order's quantity was already
     * checked to be a whole number of lots when it was placed; this validates it
     * again because the bond's lot size may have been edited since, and the
     * writer would otherwise silently buy a different number of units than the
     * order says.</p>
     *
     * @throws BadRequestException the bond has no usable lot size, or the
     *                             quantity is not a whole number of lots
     */
    private long resolveNumberOfLots(Bond bond, long quantity) {

        BigDecimal lotSize = bond.getLotSize();

        if (lotSize == null) {
            throw new BadRequestException(
                    "Lot size has not been set for bond: " + bond.getIsin());
        }

        long units;

        try {
            units = lotSize.setScale(0, RoundingMode.HALF_UP).longValueExact();

        } catch (ArithmeticException unusable) {

            throw new BadRequestException(
                    "Lot size is not a usable quantity for bond: " + bond.getIsin()
                            + " (lot size " + lotSize + ")");
        }

        if (units <= 0) {
            throw new BadRequestException(
                    "Lot size must be greater than 0 for bond: " + bond.getIsin()
                            + " (lot size " + lotSize + ")");
        }

        if (quantity % units != 0) {
            throw new BadRequestException(
                    "Order quantity is not a whole number of lots for bond: "
                            + bond.getIsin()
                            + " (lot size " + units + ", quantity " + quantity + ")");
        }

        return quantity / units;
    }

    /**
     * Mapped inside the transaction, while the order's associations are still
     * loadable — the caller is not transactional and could not read them.
     */
    private BondOrderPaymentResponse buildResponse(
            BondOrder order,
            DealConfirmationResponse deal,
            BondHolding holding,
            String paymentReference,
            boolean replayed) {

        Bond bond = order.getBond();

        return BondOrderPaymentResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .bondId(bond.getId())
                .bondName(bond.getName())
                .isin(bond.getIsin())
                .quantity(order.getQuantity())
                .pricePerUnit(order.getPricePerUnit())
                .totalAmount(order.getTotalAmount())
                .dealConfirmationId(deal == null ? null : deal.getDealConfirmationId())
                .dealReference(deal == null ? null : deal.getDealReference())
                .dealStatus(deal == null ? null : deal.getStatus())
                .paymentReference(paymentReference)
                .holdingQuantity(holding == null ? 0L : holding.getQuantity())
                .replayed(replayed)
                .build();
    }
}
