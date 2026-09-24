package com.click4bonds.app.Modules.Bond.Controller;

import java.util.List;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Bond.Dto.BondIssuerBulkItem;
import com.click4bonds.app.Modules.Bond.Dto.BondPriceUpdateRequest;
import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Dto.BulkIssuerResponse;
import com.click4bonds.app.Modules.Bond.Dto.CreateBondRequest;
import com.click4bonds.app.Modules.Bond.Dto.CreateIssuerRequest;
import com.click4bonds.app.Modules.Bond.Dto.IssuerPageResponse;
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Dto.UpdateBondRequest;
import com.click4bonds.app.Modules.Bond.Dto.UpdateIssuerRequest;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.IssuerService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

/**
 * Admin bond and issuer endpoints.
 *
 * Mapped at /api/admin so that bonds and issuers can live under
 * separate paths: /api/admin/bonds/** and /api/admin/issuers.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBondController {

        private final BondService bondService;
        private final IssuerService issuerService;

        @PostMapping("/bonds")
        public ResponseEntity<BondResponse> createBond(
                        @Valid @RequestBody CreateBondRequest request,
                        @AuthenticationPrincipal Jwt jwt) throws BadRequestException {

                // The filter has already rejected any token whose subject is not
                // a user identifier, so this cannot fail on an authenticated
                // request.
                UUID adminId = UUID.fromString(jwt.getSubject());

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(
                                                bondService.createBond(
                                                                request,
                                                                adminId));
        }

        @PatchMapping("/bonds/prices")
        public ResponseEntity<List<BondResponse>> updatePrices(
                        @Valid @RequestBody @NotEmpty List<@Valid BondPriceUpdateRequest> requests) throws BadRequestException {

                return ResponseEntity.ok(
                                bondService.updatePrices(requests));
        }

        @GetMapping("/bonds")
        public ResponseEntity<Page<BondResponse>> getBonds(
                        @RequestParam(required = false) String search,
                        @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

                return ResponseEntity.ok(
                                bondService.getBonds(search, null, pageable));
        }

        @GetMapping("/bonds/{isin}")
        public ResponseEntity<BondResponse> getBond(
                        @PathVariable String isin,
                        @AuthenticationPrincipal Jwt jwt) {

                UUID userId = UUID.fromString(jwt.getSubject());
                return ResponseEntity.ok(
                                bondService.getBond(isin, userId));
        }

        @PatchMapping("/bonds/{isin}")
        public ResponseEntity<BondResponse> updateBond(
                        @PathVariable String isin,
                        @Valid @RequestBody UpdateBondRequest request) throws BadRequestException {

                return ResponseEntity.ok(
                                bondService.updateBond(isin, request));
        }

        @PatchMapping("/bonds/{isin}/activate")
        public ResponseEntity<BondResponse> activateBond(
                        @PathVariable String isin) throws BadRequestException {

                return ResponseEntity.ok(
                                bondService.activateBond(isin));
        }

        @PatchMapping("/bonds/{isin}/suspend")
        public ResponseEntity<BondResponse> suspendBond(
                        @PathVariable String isin) throws BadRequestException {

                return ResponseEntity.ok(
                                bondService.suspendBond(isin));
        }

        @DeleteMapping("/bonds/{isin}")
        public ResponseEntity<Void> cancelBond(
                        @PathVariable String isin) throws BadRequestException {

                bondService.cancelBond(isin);

                return ResponseEntity.noContent().build();
        }

        // =========================================================
        // ISSUER
        // =========================================================

        /**
         * Lists all issuers, cursor paginated, with an optional
         * search over the issuer name.
         *
         * The first page is fetched without {@code cursor}. Pass the
         * {@code nextCursor} of a response back as {@code cursor} to
         * fetch the following page; {@code nextCursor} is NULL on
         * the last page. {@code hasNext} tells the caller up front.
         *
         * @param search case-insensitive substring of the name.
         *               NULL or blank returns every issuer.
         * @param cursor opaque cursor from the previous page.
         * @param size   page size, 1-100. Defaults to 20.
         */
        @GetMapping("/issuers")
        public ResponseEntity<IssuerPageResponse> getIssuers(
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String cursor,
                        @RequestParam(required = false) Integer size) {

                return ResponseEntity.ok(
                                issuerService.getIssuers(
                                                search,
                                                cursor,
                                                size));
        }

        /**
         * Attaches a brand new issuer to a bond.
         *
         * Fails if the bond already has an issuer.
         */
        @PostMapping("/bonds/{isin}/issuer")
        public ResponseEntity<IssuerResponse> addIssuer(
                        @PathVariable String isin,
                        @Valid @RequestBody CreateIssuerRequest request) {

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(
                                                issuerService.addIssuerToBond(
                                                                isin,
                                                                request));
        }

        /**
         * Partially updates the issuer already attached to a bond.
         */
        @PatchMapping("/bonds/{isin}/issuer")
        public ResponseEntity<IssuerResponse> updateIssuer(
                        @PathVariable String isin,
                        @Valid @RequestBody UpdateIssuerRequest request) {

                return ResponseEntity.ok(
                                issuerService.updateIssuer(
                                                isin,
                                                request));
        }

        @GetMapping("/bonds/{isin}/issuer")
        public ResponseEntity<IssuerResponse> getIssuer(
                        @PathVariable String isin) {

                return ResponseEntity.ok(
                                issuerService.getIssuerByIsin(isin));
        }

        /**
         * Bulk insert/upsert of issuer details for many bonds.
         *
         * Each row is identified by ISIN. Rows are independent, so a
         * bad row is reported in {@code errors} while the rest are
         * still saved.
         */
        @PostMapping("/bonds/issuers/bulk")
        public ResponseEntity<BulkIssuerResponse> bulkUpsertIssuers(
                        @Valid @RequestBody @NotEmpty List<@Valid BondIssuerBulkItem> items) {

                return ResponseEntity.ok(
                                issuerService.bulkUpsertIssuers(items));
        }
}
