package com.click4bonds.app.Modules.Admin.Controller;

import java.util.Set;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Admin.Dto.AdminDealSummaryResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateClientDealRequest;
import com.click4bonds.app.Modules.Admin.Service.AdminDealService;
import com.click4bonds.app.Modules.Common.Web.PaginationSorts;
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

    /** Properties the employee's deal listing will sort by. */
    private static final Set<String> DEAL_SORTABLE_PROPERTIES = Set.of(
            "createdAt",
            "dealDate",
            "dealReference",
            "status",
            "totalAmount");

    /** Newest first, the order an employee works through recent deals in. */
    private static final Sort DEAL_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AdminDealService adminDealService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'DEAL_CREATION')")
    public ResponseEntity<DealConfirmationResponse> createDeal(
            @Valid @RequestBody CreateClientDealRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adminDealService.create(
                        request,
                        UUID.fromString(jwt.getSubject())));
    }

    /**
     * The deals the authenticated employee created.
     *
     * <p>Scoped to the caller in the query itself, so an employee sees only their
     * own deals. {@code search} is matched against the deal reference, client,
     * bond and ISIN.</p>
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'DEAL_CREATION')")
    public ResponseEntity<Page<AdminDealSummaryResponse>> getMyDeals(
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
                adminDealService.getDealsCreatedBy(
                        UUID.fromString(jwt.getSubject()),
                        search,
                        PaginationSorts.restrict(
                                pageable,
                                DEAL_SORTABLE_PROPERTIES,
                                DEAL_DEFAULT_SORT)));
    }
}
