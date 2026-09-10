package com.click4bonds.app.Modules.OTP.Service;

import java.util.Locale;

import com.click4bonds.app.Modules.OTP.Model.OtpType;

/**
 * Builds every Redis key the OTP module owns.
 *
 * <p>Key layout:</p>
 * <pre>
 * otp:v2:email:{normalized-email}          otp:v2:sms:{normalized-phone}
 * otp:v2:cooldown:email:{normalized-email} otp:v2:cooldown:sms:{normalized-phone}
 * </pre>
 *
 * <p>The channel is part of the key, so email and SMS state can never
 * collide. Nothing outside this class is allowed to assemble these keys by
 * hand — that is what keeps the two namespaces honest.</p>
 *
 * <p>{@code v2} is the serialization generation, not a schema nicety. Entries
 * written before values became JSON hold JDK-serialized streams that no longer
 * parse; reading one would surface as an unreadable-value failure rather than
 * as the absent code it really is. Moving the namespace retires them outright —
 * they expire on their own within one OTP lifetime, and until then the new keys
 * simply do not see them.</p>
 */
public final class OtpKeyFactory {

    private static final String ROOT = "otp";

    /** Serialization generation. Bump whenever the stored value format changes. */
    private static final String VERSION = "v2";

    private static final String COOLDOWN = "cooldown";

    private OtpKeyFactory() {
    }

    /**
     * Key holding the pending OTP state.
     *
     * @param identifier already normalized by {@link IdentifierNormalizer}
     */
    public static String otpKey(OtpType type, String identifier) {
        return ROOT + ":" + VERSION + ":" + segment(type) + ":" + identifier;
    }

    /**
     * Key guarding the resend cooldown. Kept separate from the OTP key so the
     * cooldown can outlive, or expire before, the code it protects.
     *
     * @param identifier already normalized by {@link IdentifierNormalizer}
     */
    public static String cooldownKey(OtpType type, String identifier) {
        return ROOT + ":" + VERSION + ":" + COOLDOWN + ":" + segment(type) + ":" + identifier;
    }

    private static String segment(OtpType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
