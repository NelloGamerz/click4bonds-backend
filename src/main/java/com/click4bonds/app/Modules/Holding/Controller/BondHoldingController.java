package com.click4bonds.app.Modules.Holding.Controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Holding.Model.BondHolding;
import com.click4bonds.app.Modules.Holding.Service.BondHoldingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class BondHoldingController {

    private final BondHoldingService holdingService;

    @GetMapping("/my")
    public ResponseEntity<Page<BondHolding>> getMyHoldings(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {

        String customerId = jwt.getSubject();

        return ResponseEntity.ok(
                holdingService.getMyHoldings(
                        customerId,
                        pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BondHolding> getHolding(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        String customerId = jwt.getSubject();

        return ResponseEntity.ok(
                holdingService.getHolding(
                        customerId,
                        id));
    }
}
