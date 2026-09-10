package com.click4bonds.app.Modules.Email.Model;

/**
 * Provider-agnostic description of an email that has to be delivered.
 *
 * <p>This is the only payload that crosses the {@code EmailProvider} boundary,
 * so provider implementations never see business objects, templates or
 * provider specific option types.</p>
 *
 * @param to      recipient email address
 * @param subject subject line
 * @param html    rendered HTML body
 */
public record EmailRequest(
        String to,
        String subject,
        String html) {

    public EmailRequest {

        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Email recipient is required");
        }

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Email subject is required");
        }

        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("Email body is required");
        }
    }
}
