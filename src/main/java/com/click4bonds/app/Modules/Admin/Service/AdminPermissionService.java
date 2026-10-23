package com.click4bonds.app.Modules.Admin.Service;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.AdminProfileRepository;
import com.click4bonds.app.Modules.User.Service.UserService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Decides whether a back-office account may use a module.
 *
 * <p>Asked from {@code @PreAuthorize} expressions on the admin controllers, and
 * answered from this database rather than from the caller's token. That is the
 * point of the class: a token's {@code role} claim is a statement about the
 * moment the token was minted, so a permission carried in one would keep working
 * after it had been withdrawn. Reading the grants here means a revoked module
 * stops working at the account's next request instead of at its next token
 * refresh.</p>
 *
 * <p>Nothing the browser sends is consulted. The subject comes from the verified
 * token, the role is re-read from the account's row, and the grants come from
 * the profile. A client that sends a permission in a header, a body or a query
 * parameter has no effect on the answer, which is what makes the frontend's idea
 * of what a user may do irrelevant to whether they may do it.</p>
 *
 * <p>Every path that cannot be resolved denies. An unauthenticated caller, a
 * subject that is not an identifier, an account that no longer exists, a
 * customer, a module name that is not one of {@link AdminModule}'s, or a staff
 * account with no profile all answer {@code false} — so the failure mode of
 * anything unexpected here is a refused request rather than an allowed one.</p>
 *
 * <p>{@code ADMIN} is not constrained by its grants. It is the role that manages
 * the grants, and a stored set that could lock every administrator out of the
 * surface that repairs it is a worse failure than an administrator having
 * access they did not strictly need. {@code EMPLOYEE} is constrained, and is the
 * role this class exists for.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminPermissionService {

    private final UserService userService;
    private final AdminProfileRepository adminProfileRepository;

    /**
     * Whether the authenticated account may use the named module.
     *
     * <p>Takes the module as a {@code String} because that is what a
     * {@code @PreAuthorize} expression can supply — SpEL can pass a literal, not
     * an enum reference. An unrecognised name denies, which is why
     * {@link AdminModule#fromName} returns null instead of throwing: a typo in
     * one of those literals should refuse that endpoint, not turn it into a
     * 500.</p>
     *
     * @param authentication the request's authentication, or null if there is none
     * @param module         module name, as spelled in {@link AdminModule}
     * @return {@code true} only when the account is a staff account and is
     *         permitted the module
     */
    public boolean hasModule(Authentication authentication, String module) {

        UUID userId = subjectOf(authentication);

        if (userId == null) {
            return false;
        }

        User user = userService.findUser(userId).orElse(null);

        if (user == null) {
            return false;
        }

        // Re-read from the account rather than trusting the token's claim. Doing
        // so is what makes a role change take effect immediately too, rather
        // than only at the next token refresh.
        UserRole role = user.getRole();

        if (role == UserRole.CUSTOMER) {
            return false;
        }

        if (role == UserRole.ADMIN) {
            return true;
        }

        AdminModule requested = AdminModule.fromName(module);

        if (requested == null) {

            log.warn("Permission check for an unknown module: {}", module);

            return false;
        }

        return adminProfileRepository.findByUserId(userId)
                .map(profile -> profile.allows(requested))
                .orElse(false);
    }

    /**
     * Reads the account identifier off the authentication.
     *
     * <p>The subject of the bearer token is {@code User.id}, and
     * {@code JwtAuthenticationFilter} installs the {@link Jwt} itself as the
     * principal, so that is where the identifier is. Anything else — no
     * authentication, a principal of another kind, a subject that is not a
     * well-formed identifier — is answered with null, and the caller denies.</p>
     *
     * @param authentication the request's authentication, or null
     * @return the account identifier, or null if it cannot be established
     */
    private static UUID subjectOf(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof Jwt jwt)) {
            return null;
        }

        try {
            return UUID.fromString(jwt.getSubject());

        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }
}
