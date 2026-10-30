package com.click4bonds.app.Modules.Admin.Controller;

import java.util.Set;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Admin.Dto.AdminDealCalculationResponse;
import com.click4bonds.app.Modules.Admin.Dto.AdminDealSummaryResponse;
import com.click4bonds.app.Modules.Admin.Dto.CalculateDealPricingRequest;
import com.click4bonds.app.Modules.Admin.Dto.CreateClientDealRequest;
import com.click4bonds.app.Modules.Admin.Service.AdminDealService;
import com.click4bonds.app.Modules.Common.Web.PaginationSorts;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationResponse;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationDocumentReader;

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
    private final DealConfirmationDocumentReader documentReader;

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
     * Prices one deal, or solves the yield it was priced at.
     *
     * <p>Send exactly one of {@code price} or {@code annualYtm}; the other is
     * calculated and both are saved on this deal. The bond's own price and yield
     * are untouched, so a negotiated price for one client never restates the
     * bond. The calculation is measured to the deal's value date.</p>
     */
    @PostMapping("/{dealId}/calculate")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'DEAL_CREATION')")
    public ResponseEntity<AdminDealCalculationResponse> calculateDeal(
            @PathVariable UUID dealId,
            @Valid @RequestBody CalculateDealPricingRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(adminDealService.calculate(
                UUID.fromString(jwt.getSubject()),
                dealId,
                request));
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

    /**
     * Downloads the confirmation letter for a deal the authenticated employee
     * created.
     *
     * <p>Scoped to {@code createdBy} in the query itself, so another employee's
     * reference is indistinguishable from one that does not exist — both are
     * 404.</p>
     */
    @GetMapping("/{dealReference}/document")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'DEAL_CREATION')")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable String dealReference,
            @AuthenticationPrincipal Jwt jwt) {

        return download(dealReference, jwt);
    }

    /**
     * The same download for a reference that carries slashes, e.g.
     * {@code 2026/S/OCT/144}, which arrives as several path segments.
     */
    @GetMapping("/{year}/{side}/{month}/{sequence}/document")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'DEAL_CREATION')")
    public ResponseEntity<byte[]> downloadNestedReferenceDocument(
            @PathVariable String year,
            @PathVariable String side,
            @PathVariable String month,
            @PathVariable String sequence,
            @AuthenticationPrincipal Jwt jwt) {

        return download(year + "/" + side + "/" + month + "/" + sequence, jwt);
    }

    /**
     * The same download, with the reference as a query parameter.
     *
     * <p>Preferred for references that contain slashes. A path segment holding
     * {@code /} must be percent-encoded by the client, and Spring Security's
     * default firewall rejects an encoded slash before the CORS filter runs — so
     * the browser reports a CORS failure instead of the 400 that actually
     * happened. A query parameter has no such rule.</p>
     */
    @GetMapping("/document")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'DEAL_CREATION')")
    public ResponseEntity<byte[]> downloadDocumentByReference(
            @RequestParam String reference,
            @AuthenticationPrincipal Jwt jwt) {

        return download(reference, jwt);
    }

    private ResponseEntity<byte[]> download(String dealReference, Jwt jwt) {

        DealConfirmationDocumentReader.DownloadedDocument document =
                documentReader.readCreatedBy(
                        UUID.fromString(jwt.getSubject()),
                        dealReference);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.fileName())
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(document.content());
    }
}
