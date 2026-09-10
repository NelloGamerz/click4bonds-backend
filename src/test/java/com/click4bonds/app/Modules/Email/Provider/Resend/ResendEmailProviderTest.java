package com.click4bonds.app.Modules.Email.Provider.Resend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.click4bonds.app.Modules.Email.Exception.EmailSendException;
import com.click4bonds.app.Modules.Email.Model.EmailRequest;
import com.click4bonds.app.Modules.Email.Provider.EmailProviderType;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

/**
 * Verifies the mapping onto the Resend SDK without ever calling Resend: the
 * whole client is mocked.
 */
class ResendEmailProviderTest {

    private static final String FROM = "Click4Bond <no-reply@click4bonds.com>";

    private Resend resend;
    private Emails emails;
    private ResendEmailProvider provider;

    @BeforeEach
    void setUp() {

        resend = mock(Resend.class);
        emails = mock(Emails.class);

        when(resend.emails()).thenReturn(emails);

        provider = new ResendEmailProvider(resend, FROM);
    }

    @Test
    void shouldReportResendAsItsProviderType() {
        assertEquals(EmailProviderType.RESEND, provider.type());
    }

    @Test
    void shouldSendRequestThroughResend() throws ResendException {

        when(emails.send(any(CreateEmailOptions.class)))
                .thenReturn(new CreateEmailResponse("msg_123"));

        provider.send(new EmailRequest("user@example.com", "Verify your Click4Bond account", "<p>hello</p>"));

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());

        CreateEmailOptions sent = captor.getValue();

        assertEquals(FROM, sent.getFrom());
        assertEquals("user@example.com", sent.getTo().getFirst());
        assertEquals("Verify your Click4Bond account", sent.getSubject());
        assertEquals("<p>hello</p>", sent.getHtml());
    }

    @Test
    void shouldWrapProviderFailuresInEmailSendException() throws ResendException {

        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("invalid from address"));

        EmailSendException ex = assertThrows(
                EmailSendException.class,
                () -> provider.send(new EmailRequest("user@example.com", "subject", "<p>hello</p>")));

        assertEquals("invalid from address", ex.getCause().getMessage());
        assertTrue(ex.getMessage().contains("user@example.com"));
    }

    @Test
    void shouldNotFailWhenProviderReturnsNoResponseBody() throws ResendException {

        when(emails.send(any(CreateEmailOptions.class))).thenReturn(null);

        provider.send(new EmailRequest("user@example.com", "subject", "<p>hello</p>"));

        verify(emails).send(any(CreateEmailOptions.class));
    }
}
