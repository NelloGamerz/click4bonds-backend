package com.click4bonds.app.Modules.Party.Enums;

import com.click4bonds.app.Modules.User.Enums.UserType;

/**
 * What kind of legal or natural person a {@code Party} is.
 *
 * <p>Kept separate from {@link UserType}: that enum records what a customer
 * said about themselves at sign-up, while this records the identity a party
 * represents. The two are mapped, not shared, so a change to the sign-up form
 * cannot silently change the meaning of a stored party.</p>
 */
public enum PartyType {

    INDIVIDUAL,
    COMPANY,
    LLP,
    PARTNERSHIP,
    TRUST,
    OTHER;

    /**
     * The party type a customer sign-up form's answer implies.
     *
     * <p>An HUF and an association of persons are neither an individual nor a
     * company in the sense this column stores, so both fall to {@link #OTHER}
     * rather than being forced into a shape that would misdescribe them. An
     * admin can correct the type afterwards.</p>
     */
    public static PartyType fromUserType(UserType userType) {

        if (userType == null) {
            return INDIVIDUAL;
        }

        return switch (userType) {
            case INDIVIDUAL_RESIDENT, INDIVIDUAL_NRI_NRO -> INDIVIDUAL;
            case CORPORATE -> COMPANY;
            case LLP -> LLP;
            case PARTNERSHIP_FIRM -> PARTNERSHIP;
            case TRUST -> TRUST;
            case ASSOCIATION_OF_PERSONS, HUF -> OTHER;
        };
    }
}
