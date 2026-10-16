package com.click4bonds.app.Modules.Admin.Dto;

import java.util.Set;

import com.click4bonds.app.Modules.User.Enums.AdminModule;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Enums.UserStatus;

/**
 * Everything an administrator may change about an account, in one request.
 *
 * <p>Replaces the separate status, role and access-config writes. One body that
 * says what the account should look like, rather than three endpoints that each
 * say one thing about it — which also means a change that spans several fields
 * either lands entirely or not at all, instead of leaving an account
 * half-updated when the second of three calls fails.</p>
 *
 * <p><strong>Absent means "leave alone".</strong> Every field is nullable and a
 * null one is not applied. That is what lets a caller send only what it is
 * changing, and it is why {@link #accessConfig} being an empty set is
 * meaningfully different from it being null: empty withdraws every grant, null
 * leaves the grants as they are.</p>
 *
 * <p>The cost of that rule is that a field cannot be <em>cleared</em> by sending
 * null — nothing needs to unset one today, and inventing a sentinel to express
 * it would make every request harder to read. If clearing a field ever becomes
 * necessary, it wants its own explicit signal rather than a second meaning for
 * null.</p>
 */
public record UpdateUserRequest(

        /**
         * The status the account should hold, or null to leave it.
         *
         * <p>Applies to any account: a customer is suspended for the same reason
         * a member of staff is.</p>
         */
        UserStatus status,

        /**
         * The role the account should hold, or null to leave it.
         *
         * <p>A change that crosses the customer/staff line is refused rather than
         * performed, because it would have to discard the account's profile — and
         * with it the KYC record or the module grants. See
         * {@code UserService#updateRole}.</p>
         */
        UserRole role,

        /**
         * The complete set of modules the account should be granted, or null to
         * leave the grants as they are.
         *
         * <p>A whole set rather than an add or a remove, so applying the same
         * request twice leaves the account in the same state — a retry after a
         * timeout cannot double-apply. An empty set withdraws every module.</p>
         */
        Set<AdminModule> accessConfig) {
}
