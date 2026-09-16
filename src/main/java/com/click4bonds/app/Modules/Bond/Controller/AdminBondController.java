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
import com.click4bonds.app.Modules.Bond.Dto.IssuerResponse;
import com.click4bonds.app.Modules.Bond.Dto.UpdateBondRequest;
import com.click4bonds.app.Modules.Bond.Dto.UpdateIssuerRequest;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.IssuerService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/bonds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBondController {

        private final BondService bondService;
        private final IssuerService issuerService;

        @PostMapping
        public ResponseEntity<BondResponse> createBond(
                        @Valid @RequestBody CreateBondRequest request,
                        @AuthenticationPrincipal Jwt jwt) throws BadRequestException {

                String adminId = jwt.getSubject();

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(
                                                bondService.createBond(
                                                                request,
                                                                adminId));
        }

        @PatchMapping("/prices")
        public ResponseEntity<List<BondResponse>> updatePrices(
                        @Valid @RequestBody @NotEmpty List<@Valid BondPriceUpdateRequest> requests) throws BadRequestException {

                return ResponseEntity.ok(
                                bondService.updatePrices(requests));
        }

        @GetMapping
        public ResponseEntity<Page<BondResponse>> getBonds(
                        @RequestParam(required = false) String search,
                        @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

                return ResponseEntity.ok(
                                bondService.getBonds(search, null, pageable));
        }

        @GetMapping("/{id}")
        public ResponseEntity<BondResponse> getBond(
                        @PathVariable String id) {

                return ResponseEntity.ok(
                                bondService.getBond(id));
        }

        @PatchMapping("/{id}")
        public ResponseEntity<BondResponse> updateBond(
                        @PathVariable UUID id,
                        @Valid @RequestBody UpdateBondRequest request) throws BadRequestException {

                return ResponseEntity.ok(
                                bondService.updateBond(id, request));
        }

        @PatchMapping("/{id}/activate")
        public ResponseEntity<BondResponse> activateBond(
                        @PathVariable UUID id) throws BadRequestException {

                return ResponseEntity.ok(
                                bondService.activateBond(id));
        }

        @PatchMapping("/{id}/suspend")
        public ResponseEntity<BondResponse> suspendBond(
                        @PathVariable UUID id) throws BadRequestException {

                return ResponseEntity.ok(
                                bondService.suspendBond(id));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> cancelBond(
                        @PathVariable UUID id) throws BadRequestException {

                bondService.cancelBond(id);

                return ResponseEntity.noContent().build();
        }

        // =========================================================
        // ISSUER
        // =========================================================

        /**
         * Attaches a brand new issuer to a bond.
         *
         * Fails if the bond already has an issuer.
         */
        @PostMapping("/{id}/issuer")
        public ResponseEntity<IssuerResponse> addIssuer(
                        @PathVariable UUID id,
                        @Valid @RequestBody CreateIssuerRequest request) {

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(
                                                issuerService.addIssuerToBond(
                                                                id,
                                                                request));
        }

        /**
         * Partially updates the issuer already attached to a bond.
         */
        @PatchMapping("/{id}/issuer")
        public ResponseEntity<IssuerResponse> updateIssuer(
                        @PathVariable UUID id,
                        @Valid @RequestBody UpdateIssuerRequest request) {

                return ResponseEntity.ok(
                                issuerService.updateIssuer(
                                                id,
                                                request));
        }

        @GetMapping("/{id}/issuer")
        public ResponseEntity<IssuerResponse> getIssuer(
                        @PathVariable UUID id) {

                return ResponseEntity.ok(
                                issuerService.getIssuerByBondId(id));
        }

        /**
         * Bulk insert/upsert of issuer details for many bonds.
         *
         * Each row is identified by ISIN. Rows are independent, so a
         * bad row is reported in {@code errors} while the rest are
         * still saved.
         */
        @PostMapping("/issuers/bulk")
        public ResponseEntity<BulkIssuerResponse> bulkUpsertIssuers(
                        @Valid @RequestBody @NotEmpty List<@Valid BondIssuerBulkItem> items) {

                return ResponseEntity.ok(
                                issuerService.bulkUpsertIssuers(items));
        }
}
