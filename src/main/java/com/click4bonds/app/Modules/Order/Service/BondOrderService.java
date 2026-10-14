package com.click4bonds.app.Modules.Order.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;
import com.click4bonds.app.Modules.Bond.Enums.BondOrderStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Order.Dto.BondOrderResponse;
import com.click4bonds.app.Modules.Order.Dto.CreateOrderRequest;
import com.click4bonds.app.Modules.Order.Model.BondOrder;
import com.click4bonds.app.Modules.Order.Repository.BondOrderRepository;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BondOrderService {

    private final BondOrderRepository orderRepository;
    private final UserService userService;
    private final BondService bondService;
    private final AnalyticsService analyticsService;

    /**
     * Outcome of a create request.
     *
     * @param response what the caller gets back
     * @param replayed true when this request was a duplicate that was answered
     *                 with the order created by the first one, and therefore
     *                 created nothing
     */
    public record Result(BondOrderResponse response, boolean replayed) {
    }

    /**
     * Places an order for the calling account.
     *
     * <p>Reachable only from the admin controller, and the buyer is always the
     * caller — the subject of the JWT, never a field in the request — so an
     * order cannot be placed in someone else's name.</p>
     *
     * <p><strong>Idempotency.</strong> When the caller supplies an
     * {@code Idempotency-Key}, a retry is answered with the order the first
     * request created instead of a second order for the same bond. That covers
     * the ordinary cases — a double click, a client that retries after a
     * timeout. Two copies arriving at the <em>same instant</em> both miss the
     * lookup, and the unique index on {@code (customer_id, idempotency_key)}
     * is what decides between them: the loser is refused by the database rather
     * than being silently replayed, because the question "which of these two
     * simultaneous requests is the retry?" has no answer the server can give
     * without the first one having committed. Either way exactly one order
     * exists, which is the property that matters.</p>
     *
     * <p>The insert is flushed immediately so a key that is already taken is
     * refused here, before the analytics event below is recorded — an event for
     * an order that was never written would be worse than no event at all.</p>
     *
     * @param callerId       the account placing the order, from the JWT
     * @param request        bond and number of lots; the units are worked out
     *                       from the bond's lot size
     * @param idempotencyKey client-supplied key, or null when none was sent
     * @return the created order, or the original one when this was a retry
     * @throws ResourceNotFoundException the account or the bond does not exist
     * @throws BadRequestException       the bond has no price or no usable lot
     *                                   size, or the lot count overflows
     */
    @Transactional
    public Result createOrder(
            UUID callerId,
            CreateOrderRequest request,
            String idempotencyKey) {

        String key = normalizeKey(idempotencyKey);

        if (key != null) {

            Optional<BondOrder> existing = orderRepository
                    .findByCustomer_IdAndIdempotencyKey(callerId, key);

            if (existing.isPresent()) {

                log.info(
                        "Duplicate order request replayed: idempotencyKey={} orderNumber={}",
                        key,
                        existing.get().getOrderNumber());

                return new Result(mapToResponse(existing.get()), true);
            }
        }

        User customer = userService.getUser(callerId);
        Bond bond = bondService.findBond(request.bondIsin());

        BigDecimal price = bond.getPrice();

        if (price == null) {
            throw new BadRequestException(
                    "Price is not available for bond: " + bond.getIsin());
        }

        /*
         * The caller sends lots, the order stores units. The lot size is a
         * property of the bond and is read from it, so a client cannot state a
         * per-lot quantity that disagrees with the bond it is buying.
         */
        long numberOfLots = request.quantity();

        long unitsPerLot = resolveLotSizeUnits(bond);

        long quantity = calculateUnits(numberOfLots, unitsPerLot, bond);

        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));

        BondOrder order = BondOrder.builder()
                .orderNumber(generateOrderNumber())
                .customer(customer)
                .bond(bond)
                .quantity(quantity)
                .pricePerUnit(price)
                .totalAmount(totalAmount)
                .status(BondOrderStatus.PENDING)
                .idempotencyKey(key)
                .build();

        BondOrder savedOrder = orderRepository.saveAndFlush(order);

        log.info(
                "Order placed: orderNumber={} isin={} numberOfLots={} quantity={} customer={}",
                savedOrder.getOrderNumber(),
                bond.getIsin(),
                numberOfLots,
                savedOrder.getQuantity(),
                callerId);

        analyticsService.track(
                AnalyticsEventType.ORDER_CREATE,
                callerId,
                null,
                bond.getId(),
                "WEB",
                "ORDER",
                java.util.Map.of(
                        "orderId", savedOrder.getId().toString(),
                        "numberOfLots", numberOfLots,
                        "quantity", savedOrder.getQuantity(),
                        "status", savedOrder.getStatus().name()));

        return new Result(mapToResponse(savedOrder), false);
    }

    private String generateOrderNumber() {

        return "ORD-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }

    /**
     * Reads the bond's lot size as a whole number of units.
     *
     * <p>The lot size is stored as a {@code BigDecimal} and rounded
     * {@code HALF_UP} to whole units, the same convention
     * {@code DealConfirmationWriter.resolveLotSize} uses — the two must agree, or
     * an order this method accepts would be one the writer refuses when the
     * payment tries to reserve it.</p>
     *
     * @throws BadRequestException the bond has no lot size, or one that is not a
     *                             positive whole number of units. Both are data
     *                             problems an admin has to fix: guessing a lot
     *                             size would size the order in units the bond
     *                             does not trade in.
     */
    private long resolveLotSizeUnits(Bond bond) {

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

        return units;
    }

    /**
     * Turns the caller's lot count into the units the order is actually for.
     *
     * @throws BadRequestException when the product does not fit in a {@code long}
     */
    private long calculateUnits(long numberOfLots, long unitsPerLot, Bond bond) {

        try {
            return Math.multiplyExact(numberOfLots, unitsPerLot);

        } catch (ArithmeticException overflow) {

            /*
             * Both factors are positive, so this is an absurd order rather than a
             * malformed one. It must not wrap around into a small number and size
             * the order at the wrong quantity.
             */
            throw new BadRequestException(
                    "Order quantity is too large for bond: " + bond.getIsin()
                            + " (" + numberOfLots + " lots x " + unitsPerLot + " units)");
        }
    }

    @Transactional(readOnly = true)
    public Page<BondOrderResponse> getMyOrders(
            UUID customerId,
            Pageable pageable) {

        Page<BondOrderResponse> orders = orderRepository
                .findByCustomer_Id(customerId, pageable)
                .map(this::mapToResponse);
        analyticsService.track(
                AnalyticsEventType.PORTFOLIO_TRANSACTION_VIEW,
                customerId,
                null,
                null,
                "WEB",
                "ORDER_HISTORY",
                java.util.Map.of(
                        "resultCount", orders.getNumberOfElements(),
                        "page", orders.getNumber()));
        return orders;
    }

    @Transactional(readOnly = true)
    public BondOrderResponse getOrder(
            UUID customerId,
            UUID orderId) {

        BondOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found"));

        if (!order.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException(
                    "You cannot access this order");
        }

        return mapToResponse(order);
    }

    private BondOrderResponse mapToResponse(
            BondOrder order) {

        return BondOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .bondId(order.getBond().getId())
                .bondName(order.getBond().getName())
                .quantity(order.getQuantity())
                .pricePerUnit(order.getPricePerUnit())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }

    /**
     * Treats a blank header as "no key" so a client that always sends the header
     * but sometimes empty does not make every request look like a retry of the
     * first one. Matches {@code DealConfirmationService.normalizeKey}.
     */
    private String normalizeKey(String idempotencyKey) {

        if (idempotencyKey == null) {
            return null;
        }

        String trimmed = idempotencyKey.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}
