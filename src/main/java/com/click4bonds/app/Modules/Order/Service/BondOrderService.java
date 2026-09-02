package com.click4bonds.app.Modules.Order.Service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Enums.BondOrderStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Order.Dto.BondOrderResponse;
import com.click4bonds.app.Modules.Order.Dto.CreateOrderRequest;
import com.click4bonds.app.Modules.Order.Model.BondOrder;
import com.click4bonds.app.Modules.Order.Repository.BondOrderRepository;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.UserRepository;
import com.click4bonds.app.Modules.User.Service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BondOrderService {

    private final BondOrderRepository orderRepository;
    private final BondRepository bondRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    // public BondOrderResponse createOrder(
    // String customerId,
    // CreateOrderRequest request) throws BadRequestException {

    // User customer = userRepository.findByClerkUserId(customerId)
    // .orElseThrow(() -> new ResourceNotFoundException(
    // "Customer not found"));

    // if (customer.getRole() != UserRole.CUSTOMER) {
    // throw new ForbiddenException(
    // "Only customers can purchase bonds");
    // }

    // /*
    // * IMPORTANT:
    // * Lock the bond row while checking/decreasing inventory.
    // */
    // Bond bond = bondRepository.findByIdForUpdate(
    // request.getBondId()).orElseThrow(
    // () -> new ResourceNotFoundException(
    // "Bond not found"));

    // if (bond.getStatus() != BondStatus.ACTIVE) {
    // throw new BadRequestException(
    // "Bond is not available for purchase");
    // }

    // if (bond.getAvailableUnits() < request.getQuantity()) {
    // throw new BadRequestException(
    // "Not enough bond units available");
    // }

    // BigDecimal totalAmount = bond.getSellingPrice()
    // .multiply(
    // BigDecimal.valueOf(
    // request.getQuantity()));

    // bond.setAvailableUnits(
    // bond.getAvailableUnits()
    // - request.getQuantity());

    // if (bond.getAvailableUnits() == 0) {
    // bond.setStatus(BondStatus.SOLD_OUT);
    // }

    // BondOrder order = BondOrder.builder()
    // .orderNumber(generateOrderNumber())
    // .customer(customer)
    // .bond(bond)
    // .quantity(request.getQuantity())
    // .pricePerUnit(bond.getSellingPrice())
    // .totalAmount(totalAmount)
    // .status(BondOrderStatus.PAYMENT_PENDING)
    // .build();

    // return mapToResponse(
    // orderRepository.save(order));
    // }

    @Transactional
    public BondOrder createOrder(
            String customerId,
            CreateOrderRequest request) {

        // User customer = userRepository
        //         .findById(customerId)
        //         .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        User customer = userService.getUserByClerkId(customerId);

        Bond bond = bondRepository
                .findByIsin(request.bondIsin())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with ISIN: " + request.bondIsin()));

        BigDecimal price = bond.getPrice();

        if (price == null) {
            throw new IllegalStateException(
                    "Price is not available for bond: " + bond.getIsin());
        }

        BigDecimal totalAmount = price
                .multiply(BigDecimal.valueOf(request.quantity()));

        BondOrder order = BondOrder.builder()
                .orderNumber(generateOrderNumber())
                .customer(customer)
                .bond(bond)
                .quantity(request.quantity())
                .pricePerUnit(price)
                .totalAmount(totalAmount)
                .status(BondOrderStatus.PENDING)
                .build();

        return orderRepository.save(order);
    }

    private String generateOrderNumber() {

        return "ORD-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }

    @Transactional(readOnly = true)
    public Page<BondOrderResponse> getMyOrders(
            String customerId,
            Pageable pageable) {

        return orderRepository
                .findByCustomer_ClerkUserId(customerId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public BondOrderResponse getOrder(
            String customerId,
            UUID orderId) {

        BondOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found"));

        if (!order.getCustomer().getClerkUserId().equals(customerId)) {
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
}
