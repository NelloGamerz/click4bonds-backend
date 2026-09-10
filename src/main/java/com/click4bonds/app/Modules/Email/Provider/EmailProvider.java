package com.click4bonds.app.Modules.Email.Provider;

import com.click4bonds.app.Modules.Email.Model.EmailRequest;

/**
 * Boundary between the application and whatever delivers the email.
 *
 * <p>Business code never depends on a concrete provider: it talks to
 * {@code EmailService}, which delegates to the single {@link EmailProvider}
 * selected by the {@code email.provider} property. Adding a new provider means
 * adding one implementation of this interface — nothing else changes.</p>
 */
public interface EmailProvider {

    /**
     * @return the provider this implementation delivers through
     */
    EmailProviderType type();

    /**
     * Sends the given email.
     *
     * @param request fully rendered email
     * @throws com.click4bonds.app.Modules.Email.Exception.EmailSendException if the provider
     *                                                                        rejects or cannot be
     *                                                                        reached
     */
    void send(EmailRequest request);
}
