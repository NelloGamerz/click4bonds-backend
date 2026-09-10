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
}
