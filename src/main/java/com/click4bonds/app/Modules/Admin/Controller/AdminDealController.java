package com.click4bonds.app.Modules.Admin.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Admin.Dto.CreateClientDealRequest;
import com.click4bonds.app.Modules.Admin.Service.AdminDealService;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Deals created by the back office on a client's behalf.
 *
 * <p>Two gates, as on the other admin controllers: the role gate says the caller
 * is a member of staff, and {@code DEAL_CREATION} is the module grant that says
 * this employee may create deals. An {@code ADMIN} passes the module gate for
 * everything; a caller without it is refused by Spring Security, which surfaces
 * as a 403.</p>
 */
@RestController
@RequestMapping("/api/admin/deals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
public class AdminDealController {

    private final AdminDealService adminDealService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'DEAL_CREATION')")
    public ResponseEntity<DealConfirmationResponse> createDeal(
            @Valid @RequestBody CreateClientDealRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adminDealService.create(request));
    }
}
