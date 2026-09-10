package com.click4bonds.app.Modules.Email.Provider;

import java.util.List;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.Email.Config.EmailProperties;
import com.click4bonds.app.Modules.Email.Exception.UnsupportedEmailProviderException;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the {@link EmailProvider} selected by {@code email.provider}.
 *
 * <p>Selection happens once, while the application context starts, so an
 * unknown or unimplemented provider stops the application instead of failing
 * on the first email. Every {@link EmailProvider} bean found on the classpath
 * is a candidate; the one whose {@link EmailProvider#type()} matches the
 * configured value wins.</p>
 */
@Component
@Slf4j
public class EmailProviderFactory {

    private final EmailProvider provider;

    public EmailProviderFactory(List<EmailProvider> providers, EmailProperties emailProperties) {

        List<EmailProviderType> available = providers.stream()
                .map(EmailProvider::type)
                .toList();

        EmailProviderType configured = EmailProviderType.fromConfig(emailProperties.getProvider());

        this.provider = providers.stream()
                .filter(candidate -> candidate.type() == configured)
                .findFirst()
                .orElseThrow(() -> new UnsupportedEmailProviderException(
                        "Email provider '" + configured + "' is configured through 'email.provider' but no "
                                + "implementation is available. Registered providers: " + available));

        log.info("Email provider initialised: {} ({})", configured, provider.getClass().getSimpleName());
    }

    /**
     * @return the provider that every email goes through
     */
    public EmailProvider getProvider() {
        return provider;
    }
}
