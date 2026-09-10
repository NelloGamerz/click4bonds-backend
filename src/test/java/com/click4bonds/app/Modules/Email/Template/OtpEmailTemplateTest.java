package com.click4bonds.app.Modules.Email.Template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class OtpEmailTemplateTest {

    @Test
    void shouldExposeVerificationSubject() {
        assertEquals("Verify your Click4Bond account", OtpEmailTemplate.SUBJECT);
    }

    @Test
    void shouldRenderOtpAndExpiry() {

        String html = OtpEmailTemplate.render("483920", 15);

        assertTrue(html.contains("483920"), "OTP must appear in the email");
        assertTrue(html.contains("15 minutes"),
                "The email must state the expiry it was rendered with, not a fixed one");
        assertTrue(html.contains("Email verification"), "Heading must be present");
    }

    @Test
    void shouldRenderClick4BondBrandingAndSecurityNotice() {

        String html = OtpEmailTemplate.render("483920", 15);

        assertTrue(html.contains("Click4Bond"), "Brand must be present");
        assertTrue(html.contains("If you did not request this code, you can safely ignore this email."));
        assertTrue(html.contains("never share this code"), "Security warning must be present");
    }

    @Test
    void shouldUseDefaultExpiryWhenNotSpecified() {

        String html = OtpEmailTemplate.render("483920");

        assertTrue(html.contains(OtpEmailTemplate.DEFAULT_EXPIRY_MINUTES + " minutes"));
    }

    @Test
    void shouldNotLeaveUnresolvedPlaceholders() {

        String html = OtpEmailTemplate.render("483920", 15);

        assertFalse(html.contains("{{"), "Every placeholder must be substituted");
    }

    @Test
    void shouldNotUseJavaScriptOrExternalStylesheets() {

        String html = OtpEmailTemplate.render("483920", 15);

        assertFalse(html.contains("<script"), "Email clients block JavaScript");
        assertFalse(html.contains("<link"), "Email clients block external stylesheets");
        assertFalse(html.contains("<style"), "Styling must be inline");
    }

    @Test
    void shouldRejectBlankOtp() {

        assertThrows(IllegalArgumentException.class, () -> OtpEmailTemplate.render("  ", 15));
        assertThrows(IllegalArgumentException.class, () -> OtpEmailTemplate.render(null, 15));
    }
}
