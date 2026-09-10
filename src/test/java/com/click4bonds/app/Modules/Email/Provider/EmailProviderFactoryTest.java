package com.click4bonds.app.Modules.Email.Provider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Email.Config.EmailProperties;
import com.click4bonds.app.Modules.Email.Exception.UnsupportedEmailProviderException;
import com.click4bonds.app.Modules.Email.Model.EmailRequest;

class EmailProviderFactoryTest {

    @Test
    void shouldSelectResendWhenConfiguredAsResend() {

        EmailProvider resend = new StubProvider(EmailProviderType.RESEND);

        EmailProviderFactory factory = new EmailProviderFactory(
                List.of(resend, new StubProvider(EmailProviderType.MSG91)),
                properties("resend"));

        assertSame(resend, factory.getProvider());
    }

    @Test
    void shouldSelectMsg91WhenConfiguredAsMsg91() {

        EmailProvider msg91 = new StubProvider(EmailProviderType.MSG91);

        EmailProviderFactory factory = new EmailProviderFactory(
                List.of(new StubProvider(EmailProviderType.RESEND), msg91),
                properties("MSG91"));

        assertSame(msg91, factory.getProvider());
    }

    @Test
    void shouldFailFastWhenConfiguredProviderHasNoImplementation() {

        UnsupportedEmailProviderException ex = assertThrows(
                UnsupportedEmailProviderException.class,
                () -> new EmailProviderFactory(
                        List.of(new StubProvider(EmailProviderType.RESEND)),
                        properties("msg91")));

        assertTrue(ex.getMessage().contains("MSG91"));
        assertTrue(ex.getMessage().contains("no implementation"));
    }

    @Test
    void shouldFailFastForUnsupportedProviderConfiguration() {

        UnsupportedEmailProviderException ex = assertThrows(
                UnsupportedEmailProviderException.class,
                () -> new EmailProviderFactory(
                        List.of(new StubProvider(EmailProviderType.RESEND)),
                        properties("mailchimp")));

        assertTrue(ex.getMessage().contains("mailchimp"));
    }

    @Test
    void shouldFailFastWhenNoProviderIsRegisteredAtAll() {

        assertThrows(
                UnsupportedEmailProviderException.class,
                () -> new EmailProviderFactory(List.of(), properties("resend")));
    }

    private EmailProperties properties(String provider) {

        EmailProperties emailProperties = new EmailProperties();
        emailProperties.setProvider(provider);
        return emailProperties;
    }

    private static final class StubProvider implements EmailProvider {

        private final EmailProviderType type;

        private StubProvider(EmailProviderType type) {
            this.type = type;
        }

        @Override
        public EmailProviderType type() {
            return type;
        }

        @Override
        public void send(EmailRequest request) {
            throw new UnsupportedOperationException("stub");
        }
    }
}
