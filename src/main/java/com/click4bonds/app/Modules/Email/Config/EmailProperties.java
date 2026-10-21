package com.click4bonds.app.Modules.Email.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration of the email module.
 *
 * <pre>
 * email:
 *   provider: resend
 *   deal-confirmation:
 *     enabled: true
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    /**
     * Provider used to deliver every email. Must match an
     * {@code EmailProviderType} constant, otherwise the application fails to
     * start.
     */
    private String provider = "resend";

    /**
     * Deal confirmation delivery settings.
     */
    private DealConfirmation dealConfirmation = new DealConfirmation();

    /**
     * Whether the confirmation letter is emailed to the customer once it has
     * been generated.
     *
     * <p>Separate from {@code document.enabled}, which decides whether a letter
     * is produced at all: an environment can store letters without mailing
     * them, and switching this off must not stop a document being generated.</p>
     */
    @Data
    public static class DealConfirmation {

        /** When false the letter is still generated and stored, just not sent. */
        private boolean enabled = true;
    }
}
