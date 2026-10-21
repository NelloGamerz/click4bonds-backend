package com.click4bonds.app.Modules.Email.Provider.Resend;

import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.Email.Exception.EmailSendException;
import com.click4bonds.app.Modules.Email.Model.EmailAttachment;
import com.click4bonds.app.Modules.Email.Model.EmailRequest;
import com.click4bonds.app.Modules.Email.Provider.EmailProvider;
import com.click4bonds.app.Modules.Email.Provider.EmailProviderType;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.Attachment;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link EmailProvider} backed by the Resend REST API.
 *
 * <p>The {@link Resend} client itself is created by the application's existing
 * {@code com.click4bonds.app.Config.ResendConfig} bean; this class only maps
 * the provider-agnostic {@link EmailRequest} onto Resend's request model and
 * translates Resend failures into {@link EmailSendException}.</p>
 *
 * <p>This is the only class in the application allowed to depend on the Resend
 * SDK. Logs deliberately contain no API key and no message content — email
 * bodies carry one-time codes.</p>
 */
@Component
@Slf4j
public class ResendEmailProvider implements EmailProvider {

    private final Resend resend;
    private final String from;

    public ResendEmailProvider(
            Resend resend,
            @Value("${resend.from}") String from) {

        this.resend = resend;
        this.from = from;
    }

    @Override
    public EmailProviderType type() {
        return EmailProviderType.RESEND;
    }

    @Override
    public void send(EmailRequest request) {

        CreateEmailOptions.Builder options = CreateEmailOptions.builder()
                .from(from)
                .to(request.to())
                .subject(request.subject())
                .html(request.html());

        /*
         * Resend takes attachment content inline, base64 encoded, so the bytes
         * the caller already holds are encoded here rather than uploaded
         * anywhere first.
         */
        List<Attachment> attachments = request.attachments().stream()
                .map(ResendEmailProvider::toAttachment)
                .toList();

        if (!attachments.isEmpty()) {
            options.attachments(attachments);
        }

        try {

            CreateEmailResponse response = resend.emails().send(options.build());

            log.info(
                    "Resend accepted email to {} with subject '{}' (message id: {})",
                    request.to(),
                    request.subject(),
                    response == null ? "unknown" : response.getId());

        } catch (ResendException ex) {

            log.error(
                    "Resend rejected email to {} with subject '{}': {}",
                    request.to(),
                    request.subject(),
                    ex.getMessage());

            throw new EmailSendException(
                    "Resend could not send the email to " + request.to(),
                    ex);
        }
    }

    /**
     * Maps an attachment onto Resend's model.
     *
     * <p>The content is sent inline as base64, as the API requires.</p>
     */
    private static Attachment toAttachment(EmailAttachment attachment) {

        return Attachment.builder()
                .fileName(attachment.fileName())
                .contentType(attachment.contentType())
                .content(Base64.getEncoder().encodeToString(attachment.content()))
                .build();
    }
}
