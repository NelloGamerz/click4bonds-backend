package com.click4bonds.app.Modules.Admin.Controller;

import java.util.Set;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Admin.Dto.AdminUserDetailsResponse;
import com.click4bonds.app.Modules.Admin.Dto.AdminUserSummaryResponse;
import com.click4bonds.app.Modules.Admin.Dto.UpdateContactInquiryStatusRequest;
import com.click4bonds.app.Modules.Admin.Service.AdminUserService;
import com.click4bonds.app.Modules.Common.Web.PaginationSorts;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryAdminResponse;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

        /**
         * Properties the customer listing will sort by.
         *
         * <p>Closed on purpose. {@code UserRepository.findUsers} orders nothing
         * itself, so without a bound here the {@code ?sort=} parameter would reach
         * the database as whatever property the caller named — see
         * {@link PaginationSorts} for why that is worth refusing. These are the
         * columns the admin table shows.</p>
         */
        private static final Set<String> USER_SORTABLE_PROPERTIES = Set.of(
                        "createdAt",
                        "updatedAt",
                        "firstName",
                        "lastName",
                        "email",
                        "role",
                        "status");

        /**
         * Newest first. The listing is a worklist — an administrator looking at it
         * is usually looking for an account that just appeared — and it is also
         * the order that makes paging stable, since {@code findUsers} has no
         * ordering of its own.
         */
        private static final Sort USER_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

        /**
         * Properties the contact inquiry listing will sort by.
         *
         * <p>Closed for the same reason as the customer listing above, and
         * because the query behind this one no longer sorts at all — ordering
         * now comes only from the pageable, so the pageable is the only thing
         * deciding what the database does.</p>
         */
        private static final Set<String> INQUIRY_SORTABLE_PROPERTIES = Set.of(
                        "createdAt",
                        "updatedAt",
                        "name",
                        "email",
                        "status");

        /**
         * Newest first, for the same reason as the customer listing: inquiries
         * arrive over time and the recent ones are what get worked on.
         */
        private static final Sort INQUIRY_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

        private final AdminUserService adminUserService;

        @GetMapping
        public ResponseEntity<Page<AdminUserSummaryResponse>> getUsers(
                        @RequestParam(required = false) UserRole role,
                        @RequestParam(required = false) String search,
                        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

                return ResponseEntity.ok(
                                adminUserService.getUsers(
                                                role,
                                                search,
                                                PaginationSorts.restrict(
                                                                pageable,
                                                                USER_SORTABLE_PROPERTIES,
                                                                USER_DEFAULT_SORT)));
        }

        @GetMapping("/{id}/details")
        public ResponseEntity<AdminUserDetailsResponse> getUserDetails(
                        @PathVariable UUID id) {

                return ResponseEntity.ok(
                                adminUserService.getUserDetails(id));
        }

        @PatchMapping("/{id}/status")
        public ResponseEntity<AdminUserDetailsResponse> updateStatus(
                        @PathVariable UUID id,
                        @RequestParam UserStatus status) {

                return ResponseEntity.ok(
                                adminUserService.updateUserStatus(
                                                id,
                                                status));
        }

        @PatchMapping("/{id}/role")
        public ResponseEntity<AdminUserDetailsResponse> updateRole(
                        @PathVariable UUID id,
                        @RequestParam UserRole role) {

                return ResponseEntity.ok(
                                adminUserService.updateUserRole(
                                                id,
                                                role));
        }

        @GetMapping("/contact-inquiries")
        public ResponseEntity<Page<ContactInquiryAdminResponse>> getContactInquiries(
                        @RequestParam(required = false) ContactInquiryStatus status,
                        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

                return ResponseEntity.ok(
                                adminUserService.getContactInquiries(
                                                status,
                                                PaginationSorts.restrict(
                                                                pageable,
                                                                INQUIRY_SORTABLE_PROPERTIES,
                                                                INQUIRY_DEFAULT_SORT)));
        }

        @PatchMapping("/contact-inquiries/{inquiryId}/status")
        public ContactInquiryAdminResponse updateContactInquiryStatus(
                        @PathVariable UUID inquiryId,
                        @Valid @RequestBody UpdateContactInquiryStatusRequest request) {

                return adminUserService.updateContactInquiryStatus(
                                inquiryId,
                                request.status());
        }

}
