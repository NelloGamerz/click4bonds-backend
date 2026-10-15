package com.click4bonds.app.Modules.User.Model;

import com.click4bonds.app.Modules.User.Enums.UserRole;

/**
 * Checks that a {@link User} reference on another entity holds the kind of
 * account that reference is meant for.
 *
 * <p>Several entities point at an account and mean something specific by it: an
 * order and a holding belong to the person who bought the bond, and an audit
 * entry records the member of staff who acted. Neither is expressed by the type
 * of the reference — both are a {@code User} — so the rule lives here and is
 * called from the entities that need it, rather than being restated at every
 * call site that builds one.</p>
 *
 * <p>Shared rather than written out per entity so the three callers cannot
 * drift, and so the message a violation produces names the entity that rejected
 * it.</p>
 *
 * <p>Throws {@link IllegalArgumentException} rather than an HTTP-shaped
 * exception. A reference of the wrong kind is a programming or flow error, not
 * something a caller did wrong and could correct from the response: the
 * authorization gate on the endpoint is what produces a 403 for a person, and
 * by the time one of these runs the request has already been allowed. Reporting
 * it as a 500 is the honest outcome.</p>
 *
 * <p>These run from {@code @PrePersist}/{@code @PreUpdate}, so they police what
 * this application writes. They do not police rows already in the database, and
 * they cannot — a constraint that could would have to be enforced by the
 * database, which cannot see {@code users.role} from another table's trigger
 * without a check this schema does not have.</p>
 */
public final class UserRoleAssertions {

    private UserRoleAssertions() {
    }

    /**
     * Requires an account that is a customer.
     *
     * @param user   account being referenced
     * @param entity name of the entity doing the referencing, used in the message
     * @throws IllegalArgumentException when there is no account, or it is not a
     *         {@code CUSTOMER}
     */
    public static void requireCustomer(User user, String entity) {

        if (user == null) {
            throw new IllegalArgumentException(
                    entity + " requires a customer account, but none was set");
        }

        if (user.getRole() != UserRole.CUSTOMER) {
            throw new IllegalArgumentException(
                    entity + " may only reference a CUSTOMER account, but user "
                            + user.getId() + " is " + user.getRole());
        }
    }

    /**
     * Requires an account that is a member of staff — {@code ADMIN} or
     * {@code EMPLOYEE}.
     *
     * @param user   account being referenced
     * @param entity name of the entity doing the referencing, used in the message
     * @throws IllegalArgumentException when there is no account, or it is a
     *         customer
     */
    public static void requireStaff(User user, String entity) {

        if (user == null) {
            throw new IllegalArgumentException(
                    entity + " requires a staff account, but none was set");
        }

        if (user.getRole() == UserRole.CUSTOMER) {
            throw new IllegalArgumentException(
                    entity + " may only reference an ADMIN or EMPLOYEE account, but user "
                            + user.getId() + " is " + user.getRole());
        }
    }
}
