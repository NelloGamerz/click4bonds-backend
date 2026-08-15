package com.click4bonds.app.Modules.Order.Controller;

import java.util.UUID;

import org.apache.coyote.BadRequestException;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Order.Dto.BondOrderResponse;
import com.click4bonds.app.Modules.Order.Dto.CreateOrderRequest;
import com.click4bonds.app.Modules.Order.Service.BondOrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class BondOrderController {

    private final BondOrderService orderService;

    @PostMapping
    public ResponseEntity<BondOrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) throws BadRequestException {

        String customerId = jwt.getSubject();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        orderService.createOrder(
                                customerId,
                                request
                        )
                );
    }

    @GetMapping("/my")
    public ResponseEntity<Page<BondOrderResponse>> getMyOrders(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        String customerId = jwt.getSubject();

        return ResponseEntity.ok(
                orderService.getMyOrders(
                        customerId,
                        pageable
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<BondOrderResponse> getOrder(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {

        String customerId = jwt.getSubject();

        return ResponseEntity.ok(
                orderService.getOrder(
                        customerId,
                        id
                )
        );
    }
}
