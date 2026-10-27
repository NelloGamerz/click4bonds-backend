package com.click4bonds.app.Modules.User.Enums;

/**
 * The modules a back-office account can be granted access to.
 *
 * <p>One constant per admin surface the application actually exposes, named
 * after the thing being administered rather than after the controller class
 * that happens to serve it — a controller can be split or renamed without
 * silently changing what a stored permission means.</p>
 *
 * <p>These live in the User module beside {@link UserRole} because they are a
 * property of an account, not of the admin module that reads them. The
 * permission <em>check</em> lives in the Admin module; the vocabulary it checks
 * against is part of what an account <em>is</em>, in the same way the role
 * is. Keeping it here also keeps the dependency one-way — Admin reads User, and
 * User knows nothing about Admin.</p>
 *
 * <p>An account's grants are stored on {@code AdminProfile.accessConfig} as a
 * set of these names. A set rather than a map of flags, because a permission
 * that has not been granted and one that has been explicitly refused mean the
 * same thing to every reader: no access. Nothing in the application needs to
 * tell those two apart, and a representation that cannot express the difference
 * cannot have the difference misread.</p>
 */
public enum AdminModule {

    /**
     * The customer and staff account administration surface — the listing, the
     * account details, role and status changes, and the grants themselves.
     */
    USER_MANAGEMENT,

    /**
     * The bond catalogue: creating, editing and retiring bonds.
     */
    BOND_MANAGEMENT,

    /**
     * Customer orders and the deal confirmations produced from them.
     */
    ORDER_MANAGEMENT,

    /**
     * Analytics reads.
     */
    ANALYTICS,

    /**
     * The contact inquiries customers send in.
     */
    CONTACT_INQUIRY,

    /**
     * The client records the back office maintains.
     */
    CLIENT_MANAGEMENT,

    /**
     * Creating deals on a client's behalf from the back office.
     */
    DEAL_CREATION;

    /**
     * Resolves a stored or declared module name.
     *
     * <p>Returns {@code null} rather than throwing for a name that is not one of
     * the constants above, and that is deliberate: the names appear as string
     * literals in {@code @PreAuthorize} expressions, where a compiler cannot
     * check them. A typo that silently denies an endpoint is a bug someone
     * notices and fixes; a typo that throws turns every request to that
     * endpoint into a 500, which is a worse failure and a harder one to read.
     * Denying is also the safe direction to be wrong in.</p>
     *
     * @param name module name, as spelled in a permission expression
     * @return the matching constant, or {@code null} if there is none
     */
    public static AdminModule fromName(String name) {

        if (name == null) {
            return null;
        }

        for (AdminModule module : values()) {
            if (module.name().equals(name)) {
                return module;
            }
        }

        return null;
    }
}
