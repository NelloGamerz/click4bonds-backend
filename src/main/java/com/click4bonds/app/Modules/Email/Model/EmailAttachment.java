package com.click4bonds.app.Modules.Email.Model;

/**
 * One file attached to an outgoing email.
 *
 * <p>Lives beside {@link EmailRequest} so the provider boundary stays
 * provider-agnostic: a provider implementation maps these bytes onto whatever
 * its API expects (Resend takes base64, another provider might accept a stream
 * or a path) without the caller knowing.</p>
 *
 * @param fileName    name the recipient sees, including the extension
 * @param contentType MIME type of {@code content}
 * @param content     the file itself
 */
public record EmailAttachment(
        String fileName,
        String contentType,
        byte[] content) {

    public EmailAttachment {

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Attachment file name is required");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Attachment content type is required");
        }

        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Attachment content is required");
        }
    }
}
