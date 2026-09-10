package com.click4bonds.app.Modules.Email.Provider;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.click4bonds.app.Modules.Email.Exception.UnsupportedEmailProviderException;

/**
 * Email providers that the application knows how to talk to.
 *
 * <p>The value configured through {@code email.provider} is matched against
 * this enum (case insensitive). A provider only becomes usable once an
 * {@link EmailProvider} bean reporting the matching {@link #name()} exists.</p>
 */
public enum EmailProviderType {

    RESEND,

    /**
     * Reserved for the future MSG91 integration. Declaring it here keeps the
     * configuration valid; a missing implementation is still reported at
     * startup with a clear message.
     */
    MSG91;

    /**
     * Parses the configured provider name.
     *
     * @param value value of {@code email.provider}
     * @return the matching provider type
     * @throws UnsupportedEmailProviderException if the value is blank or unknown
     */
    public static EmailProviderType fromConfig(String value) {

        if (value == null || value.isBlank()) {
            throw new UnsupportedEmailProviderException(
                    "Property 'email.provider' must be set. Supported values: " + supportedValues());
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedEmailProviderException(
                    "Unsupported email provider '" + value + "'. Supported values: " + supportedValues());
        }
    }

    private static String supportedValues() {
        return Arrays.stream(values())
                .map(EmailProviderType::name)
                .collect(Collectors.joining(", "));
    }
}
