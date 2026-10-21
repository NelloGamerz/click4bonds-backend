package com.click4bonds.app.Modules.Email.Model;

import java.util.List;

/**
 * Provider-agnostic description of an email that has to be delivered.
 *
 * <p>This is the only payload that crosses the {@code EmailProvider} boundary,
 * so provider implementations never see business objects, templates or
 * provider specific option types.</p>
 *
 * @param to          recipient email address
 * @param subject     subject line
 * @param html        rendered HTML body
 * @param attachments files to attach; empty when the message has none
 */
public record EmailRequest(
        String to,
        String subject,
        String html,
        List<EmailAttachment> attachments) {

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

        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /**
     * Builds an email without attachments.
     *
     * @param to      recipient email address
     * @param subject subject line
     * @param html    rendered HTML body
     */
    public EmailRequest(String to, String subject, String html) {
        this(to, subject, html, List.of());
    }
}
