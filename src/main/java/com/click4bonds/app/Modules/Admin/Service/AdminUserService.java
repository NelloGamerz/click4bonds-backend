package com.click4bonds.app.Modules.Admin.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Admin.Dto.AdminUserDetailsResponse;
import com.click4bonds.app.Modules.Admin.Dto.AdminUserSummaryResponse;
import com.click4bonds.app.Modules.Admin.Dto.CreateStaffUserRequest;
import com.click4bonds.app.Modules.Admin.Dto.UpdateUserRequest;
import com.click4bonds.app.Modules.Admin.Mapper.AdminUserMapper;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryAdminResponse;
import com.click4bonds.app.Modules.ContactUS.Service.ContactInquiryService;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;
import com.click4bonds.app.Modules.OTP.Model.OtpType;
import com.click4bonds.app.Modules.OTP.Service.IdentifierNormalizer;
import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.AdminProfile;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.AdminProfileRepository;
import com.click4bonds.app.Modules.User.Service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminUserService {

    private final UserService userService;
    private final ContactInquiryService contactInquiryService;
    private final AdminProfileRepository adminProfileRepository;

    @Transactional(readOnly = true)
    public Page<AdminUserSummaryResponse> getUsers(
            UserRole role,
            String search,
            Pageable pageable) {

        return userService.getUsers(role, search, pageable)
                .map(AdminUserMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailsResponse getUserDetails(UUID userId) {

        return AdminUserMapper.toDetails(userService.getUser(userId));
    }

    @Transactional(readOnly = true)
    public Page<ContactInquiryAdminResponse> getContactInquiries(
            ContactInquiryStatus status,
            Pageable pageable) {

        return contactInquiryService.getInquiries(status, pageable);
    }

    public ContactInquiryAdminResponse updateContactInquiryStatus(
            UUID inquiryId,
            ContactInquiryStatus status) {

        return contactInquiryService.updateStatus(
                inquiryId,
                status);
    }

    /**
     * Opens a staff account for an address an administrator names.
     *
     * <p>This is the only way a member of staff gets an account. Sign-up opens
     * customer accounts and always sets the role itself; there is no path
     * through it that produces an {@code ADMIN} or {@code EMPLOYEE}, which is
     * what stops a caller from promoting themselves by signing up.</p>
     *
     * <p>The account opens active, with no phone number and an unconfirmed
     * address, because it is created by someone stating an address rather than by
     * its owner doing anything. The person confirms it by signing in with a code
     * sent there — which is also the moment
     * {@code VerificationService} marks it verified — so the account is usable
     * from creation and the flag records whether the address is proven, not
     * whether the account works.</p>
     *
     * <p>A {@code CUSTOMER} is refused. The role type admits it and this endpoint
     * does not: a customer account needs the customer profile, the onboarding
     * state and the verification record that only sign-up creates, so one made
     * here would be a customer in name with none of what a customer has.</p>
     *
     * @param request the address, name and internal role to open the account with
     * @return the created account, as the admin detail view describes it
     * @throws BadRequestException   the requested role is {@code CUSTOMER}
     * @throws ConflictException     another account already owns the address
     */
    public AdminUserDetailsResponse createStaffUser(CreateStaffUserRequest request) {

        if (request.role() == UserRole.CUSTOMER) {
            throw new BadRequestException(
                    "A staff account must be an ADMIN or EMPLOYEE. "
                            + "Use sign-up to open a customer account.");
        }

        String email = IdentifierNormalizer.normalize(OtpType.EMAIL, request.email());

        if (userService.isEmailClaimed(email)) {
            throw new ConflictException(
                    "This email address is already registered to another account");
        }

        // createUser attaches the AdminProfile the role implies and creates no
        // verification record, which is what makes a staff account a staff
        // account rather than a customer without a profile.
        User saved = userService.createUser(User.builder()
                .email(email)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .role(request.role())
                .status(UserStatus.ACTIVE)
                .build());

        // The employment details and the grants come with the request rather
        // than needing calls after it, so an account can be opened complete.
        applyStaffProfile(
                saved.getAdminProfile(),
                request.employeeCode(),
                request.department(),
                request.designation(),
                request.accessConfig(),
                true);

        log.info("Created staff account {} with role {}", saved.getId(), request.role());

        return AdminUserMapper.toDetails(saved);
    }

    /**
     * Changes what an administrator may change about an account, in one call.
     *
     * <p>Absent fields are left alone — see {@link UpdateUserRequest} — so a
     * caller sends only what it is changing. Each field that is present is
     * applied, and the whole thing runs in one transaction: an account cannot be
     * left with its role changed and its grants not, which three separate
     * requests could have produced.</p>
     *
     * <p>The role is applied first, so that a change between the two staff roles
     * is in place before the staff fields are written — they belong to the same
     * {@code AdminProfile}, which such a change keeps.</p>
     *
     * <p>Staff fields on a customer are refused rather than ignored. An
     * {@code accessConfig} or a department against a customer account is a
     * caller that has mistaken what it is editing, and silently discarding it
     * would let the mistake stand as a success.</p>
     *
     * @param userId  account identifier
     * @param request the fields to change; null fields are left as they are
     * @return the account as it now stands
     * @throws ResourceNotFoundException no such account
     * @throws BadRequestException       staff fields were given for a customer
     * @throws ConflictException         a role change would cross the
     *                                   customer/staff line
     */
    public AdminUserDetailsResponse updateUser(UUID userId, UpdateUserRequest request) {

        User user = userService.getUser(userId);

        if (request.role() != null) {

            // Refuses a change across the customer/staff line, because it would
            // have to discard the profile the account holds.
            user = userService.updateRole(userId, request.role());
        }

        if (request.status() != null) {
            user = userService.updateStatus(userId, request.status());
        }

        boolean staffFieldsGiven =
                request.employeeCode() != null
                        || request.department() != null
                        || request.designation() != null
                        || request.accessConfig() != null;

        if (staffFieldsGiven) {

            AdminProfile profile = user.getAdminProfile();

            if (profile == null) {
                throw new BadRequestException(
                        "Employment details and module access apply only to ADMIN and "
                                + "EMPLOYEE accounts, but user " + userId + " is " + user.getRole());
            }

            applyStaffProfile(
                    profile,
                    request.employeeCode(),
                    request.department(),
                    request.designation(),
                    request.accessConfig(),
                    false);

            log.info("Updated staff details for user {}", userId);
        }

        return AdminUserMapper.toDetails(user);
    }

    /**
     * Writes a staff profile's employment details and grants.
     *
     * <p>A null value means different things on the two paths, which is why the
     * behaviour is a parameter rather than assumed: on a create there is nothing
     * to leave alone, so null is written through as an empty value; on an update
     * null means "unchanged" and is skipped.</p>
     *
     * <p>The grants are always a <strong>fresh set</strong>, never a mutation of
     * the existing one. The field is one JSONB value, so Hibernate compares the
     * object to decide whether the row is dirty — adding to the set in place
     * would be a change that is never written.</p>
     *
     * @param profile     staff profile to write to; never null when called
     * @param isCreate    true when creating, so null fields are written through
     */
    private void applyStaffProfile(
            AdminProfile profile,
            String employeeCode,
            String department,
            String designation,
            Set<AdminModule> accessConfig,
            boolean isCreate) {

        if (employeeCode != null || isCreate) {
            profile.setEmployeeCode(trimToNull(employeeCode));
        }

        if (department != null || isCreate) {
            profile.setDepartment(trimToNull(department));
        }

        if (designation != null || isCreate) {
            profile.setDesignation(trimToNull(designation));
        }

        // Null on a create is an account granted nothing; null on an update is
        // "leave the grants alone".
        if (accessConfig != null) {
            profile.setAccessConfig(new LinkedHashSet<>(accessConfig));
        }

        adminProfileRepository.save(profile);
    }

    /**
     * @param value a caller-supplied string
     * @return the trimmed value, or null when it carries nothing — so a field
     *         submitted as whitespace is stored as absent rather than as a blank
     */
    private static String trimToNull(String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

}
