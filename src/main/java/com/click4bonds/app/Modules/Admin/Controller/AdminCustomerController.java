package com.click4bonds.app.Modules.Admin.Controller;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Admin.Service.AdminUserService;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

        private final AdminUserService adminUserService;

        @GetMapping
        public ResponseEntity<Page<User>> getCustomers(
                        @RequestParam(required = false) String search,

                        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

                return ResponseEntity.ok(
                                adminUserService.getCustomers(
                                                search,
                                                pageable));
        }

        @PatchMapping("/{id}/status")
        public ResponseEntity<User> updateStatus(
                        @PathVariable UUID id,
                        @RequestParam UserStatus status) {

                return ResponseEntity.ok(
                                adminUserService.updateUserStatus(
                                                id,
                                                status));
        }

        @PatchMapping("/{id}/role")
        public ResponseEntity<User> updateRole(
                        @PathVariable UUID id,
                        @RequestParam UserRole role) throws JsonProcessingException {

                return ResponseEntity.ok(
                                adminUserService.updateUserRole(
                                                id,
                                                role));
        }
}
