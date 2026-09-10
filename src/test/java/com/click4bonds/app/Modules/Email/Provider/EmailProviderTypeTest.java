package com.click4bonds.app.Modules.Email.Provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Email.Exception.UnsupportedEmailProviderException;

class EmailProviderTypeTest {

    @Test
    void shouldParseConfiguredProviderIgnoringCaseAndPadding() {

        assertEquals(EmailProviderType.RESEND, EmailProviderType.fromConfig("resend"));
        assertEquals(EmailProviderType.RESEND, EmailProviderType.fromConfig("ReSeNd"));
        assertEquals(EmailProviderType.RESEND, EmailProviderType.fromConfig("  resend  "));
        assertEquals(EmailProviderType.MSG91, EmailProviderType.fromConfig("msg91"));
    }

    @Test
    void shouldRejectUnknownProviderWithSupportedValuesInMessage() {

        UnsupportedEmailProviderException ex = assertThrows(
                UnsupportedEmailProviderException.class,
                () -> EmailProviderType.fromConfig("sendgrid"));

        assertTrue(ex.getMessage().contains("sendgrid"));
        assertTrue(ex.getMessage().contains("RESEND"));
    }

    @Test
    void shouldRejectNullAndBlankProvider() {

        assertThrows(
                UnsupportedEmailProviderException.class,
                () -> EmailProviderType.fromConfig(null));

        assertThrows(
                UnsupportedEmailProviderException.class,
                () -> EmailProviderType.fromConfig("   "));
    }
}
