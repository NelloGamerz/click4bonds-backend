package com.click4bonds.app.Modules.Admin.Controller;

import java.util.Set;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Admin.Dto.AdminUserDetailsResponse;
import com.click4bonds.app.Modules.Admin.Dto.AdminUserSummaryResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateStaffUserRequest;
import com.click4bonds.app.Modules.Admin.Dto.UpdateContactInquiryStatusRequest;
import com.click4bonds.app.Modules.Admin.Dto.UpdateUserRequest;
import com.click4bonds.app.Modules.Admin.Service.AdminUserService;
import com.click4bonds.app.Modules.Common.Web.PaginationSorts;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryAdminResponse;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;
import com.click4bonds.app.Modules.User.Enums.UserRole;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Account and contact-inquiry administration.
 *
 * <p>The account surface is four endpoints, and everything an administrator can
 * do to an account goes through them: list it, open it, read it, change it.
 * There is no per-field write endpoint, so a change spanning the role, the
 * status and the module grants is one {@code PATCH} and one transaction rather
 * than a sequence that can fail halfway.</p>
 *
 * <p>Two gates, not one. The role gate — {@code hasAnyRole('ADMIN','EMPLOYEE')}
 * — says the caller is a member of staff; the module gate added to each method
 * says whether <em>this</em> member of staff may use the surface being called.
 * An {@code ADMIN} passes the module gate for everything; an {@code EMPLOYEE}
 * passes only where the grants on their profile allow.</p>
 *
 * <p>Both are restated on every mapping deliberately. A method-level
 * {@code @PreAuthorize} does not narrow the class-level one — it replaces it —
 * so a method that carried only the module check would be reachable by anyone
 * with a valid token whose module check passed, dropping the staff requirement
 * entirely.</p>
 *
 * <p>The grants are read from the database on each request by
 * {@link com.click4bonds.app.Modules.Admin.Service.AdminPermissionService}, so
 * withdrawing one takes effect at the account's next request.</p>
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
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
        @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'USER_MANAGEMENT')")
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

        /**
         * Opens a staff account for an address the administrator names.
         *
         * <p>The only way an ADMIN or EMPLOYEE account comes into existence:
         * sign-up always opens a customer, so a role cannot be acquired by
         * registering.</p>
         *
         * <p>The account opens with its address unconfirmed. The person confirms
         * it by signing in with a code sent to that address, which is what marks
         * it verified — see {@code POST /auth/email-otp} and
         * {@code VerificationService#markEmailVerified}.</p>
         *
         * @param request the address, name and internal role
         * @return 201 with the created account
         */
        @PostMapping
        @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'USER_MANAGEMENT')")
        public ResponseEntity<AdminUserDetailsResponse> createStaffUser(
                        @Valid @RequestBody CreateStaffUserRequest request) {

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(adminUserService.createStaffUser(request));
        }

        /**
         * One account, as an administrator sees it.
         *
         * <p>Carries the staff half of the account — employment details,
         * {@code emailVerified} and the module grants — so reading what an
         * account may do needs no second request. Which half is populated
         * follows from {@code role}.</p>
         *
         * @param id account identifier
         * @return the account, with whichever profile its role implies
         */
        @GetMapping("/{id}/details")
        @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'USER_MANAGEMENT')")
        public ResponseEntity<AdminUserDetailsResponse> getUserDetails(
                        @PathVariable UUID id) {

                return ResponseEntity.ok(
                                adminUserService.getUserDetails(id));
        }

        /**
         * Changes anything an administrator may change about an account.
         *
         * <p>The single write endpoint for an account. Status, role, employment
         * details and module grants all go here, so a change that spans several
         * of them is one transaction rather than a sequence of calls that can
         * fail halfway and leave the account part-updated.</p>
         *
         * <p>Fields left out of the body are left as they are. For the grants
         * that distinction matters and both are expressible: {@code accessConfig}
         * absent means "leave the grants alone", and an empty array means
         * "withdraw every grant".</p>
         *
         * <p>Grants take effect at the account's next request, not at its next
         * token refresh — {@code AdminPermissionService} reads them from the
         * database every time it is asked.</p>
         *
         * @param id      account identifier
         * @param request the fields to change; omitted fields are untouched
         * @return the account as it now stands
         */
        @PatchMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'USER_MANAGEMENT')")
        public ResponseEntity<AdminUserDetailsResponse> updateUser(
                        @PathVariable UUID id,
                        @Valid @RequestBody UpdateUserRequest request) {

                return ResponseEntity.ok(
                                adminUserService.updateUser(id, request));
        }

        @GetMapping("/contact-inquiries")
        @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CONTACT_INQUIRY')")
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
        @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'CONTACT_INQUIRY')")
        public ContactInquiryAdminResponse updateContactInquiryStatus(
                        @PathVariable UUID inquiryId,
                        @Valid @RequestBody UpdateContactInquiryStatusRequest request) {

                return adminUserService.updateContactInquiryStatus(
                                inquiryId,
                                request.status());
        }

}
