package com.click4bonds.app.Modules.OTP.Service;

import java.util.Locale;
import java.util.regex.Pattern;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.OTP.Model.OtpType;

/**
 * Canonicalises the identifier an OTP is issued for.
 *
 * <p>This is the single place where an identifier becomes a Redis key
 * fragment, so two spellings of the same address or number can never end up
 * as two different OTPs — and so a caller cannot smuggle separators or
 * whitespace into a key.</p>
 */
public final class IdentifierNormalizer {

    /**
     * Local part, '@', domain with at least one dot and a 2+ letter TLD.
     *
     * <p>Both halves are restricted to a conservative character set. That is
     * not just hygiene: the address becomes a Redis key fragment, so letting
     * an address carry ':' would let it forge a second key segment.</p>
     */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    /** Optional '+', then 7 to 15 digits (E.164 caps a number at 15). */
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");

    private IdentifierNormalizer() {
    }

    /**
     * @param type       channel the identifier belongs to
     * @param identifier raw email address or phone number
     * @return the canonical form used for key construction
     * @throws BadRequestException when the identifier is missing or malformed
     */
    public static String normalize(OtpType type, String identifier) {

        if (type == null) {
            throw new BadRequestException("OTP type is required");
        }

        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("Identifier is required");
        }

        return switch (type) {
            case EMAIL -> normalizeEmail(identifier.trim());
            case SMS -> normalizePhone(identifier.trim());
        };
    }

    /**
     * Emails are case-insensitive in practice, so the whole address is
     * lower-cased: {@code User@Example.COM} and {@code user@example.com}
     * share one OTP.
     */
    private static String normalizeEmail(String email) {

        if (!EMAIL.matcher(email).matches()) {
            throw new BadRequestException("Identifier is not a valid email address");
        }

        return email.toLowerCase(Locale.ROOT);
    }

    /**
     * Phone numbers are compared digit-for-digit with an optional leading
     * '+'. Separators people type — spaces, dashes, dots, brackets — are
     * dropped so {@code +91 98765-43210} and {@code +919876543210} agree.
     * No country code is invented: the caller supplies it in E.164 form.
     */
    private static String normalizePhone(String phone) {

        String compact = phone.replaceAll("[\\s\\-().]", "");

        if (!PHONE.matcher(compact).matches()) {
            throw new BadRequestException("Identifier is not a valid phone number");
        }

        return compact;
    }
}
