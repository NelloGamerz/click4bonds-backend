package com.click4bonds.app.Modules.Email.Provider.Resend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.Email.Exception.EmailSendException;
import com.click4bonds.app.Modules.Email.Model.EmailRequest;
import com.click4bonds.app.Modules.Email.Provider.EmailProvider;
import com.click4bonds.app.Modules.Email.Provider.EmailProviderType;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
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

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(request.to())
                .subject(request.subject())
                .html(request.html())
                .build();

        try {

            CreateEmailResponse response = resend.emails().send(options);

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
}
