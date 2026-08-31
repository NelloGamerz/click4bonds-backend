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
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondPriceUpdateRequest;
import com.click4bonds.app.Modules.Bond.Dto.CreateBondRequest;
import com.click4bonds.app.Modules.Bond.Dto.UpdateBondRequest;
import com.click4bonds.app.Modules.Bond.Service.BondService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/bonds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBondController {

        private final BondService bondService;

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
                        @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

                return ResponseEntity.ok(
                                bondService.getBonds(pageable));
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
}
