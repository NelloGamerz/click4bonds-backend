package com.click4bonds.app.Modules.Email.Template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WelcomeEmailTemplateTest {

    private static final String LOGIN_URL = "https://app.click4bond.com";

    @Test
    void shouldExposeWelcomeSubject() {
        assertEquals("Welcome to Click4Bond", WelcomeEmailTemplate.SUBJECT);
    }

    @Test
    void shouldRenderUserNameAndLoginUrl() {

        String html = WelcomeEmailTemplate.render("Karan", LOGIN_URL);

        assertTrue(html.contains("Welcome to Click4Bond, Karan"));
        assertTrue(html.contains(LOGIN_URL), "Call to action must point at the application");
        assertTrue(html.contains("Sign in to Click4Bond"));
    }

    @Test
    void shouldFallBackToNeutralGreetingWhenNameIsMissing() {

        String html = WelcomeEmailTemplate.render(null, LOGIN_URL);

        assertTrue(html.contains("Welcome to Click4Bond, there"));
        assertFalse(html.contains("null"));
    }

    @Test
    void shouldOmitCallToActionWhenNoUrlIsConfigured() {

        String html = WelcomeEmailTemplate.render("Karan", " ");

        assertFalse(html.contains("Sign in to Click4Bond"));
        assertTrue(html.contains("has been created successfully"), "Message body must still be sent");
    }

    @Test
    void shouldNotLeaveUnresolvedPlaceholders() {

        String html = WelcomeEmailTemplate.render("Karan", LOGIN_URL);

        assertFalse(html.contains("{{"), "Every placeholder must be substituted");
    }

    @Test
    void shouldKeepSupportNoteAndAvoidGuarantees() {

        String html = WelcomeEmailTemplate.render("Karan", LOGIN_URL);

        assertTrue(html.contains("If you did not create this account"));
        assertTrue(html.contains("subject to market risks"));

        String lowerCase = html.toLowerCase();
        assertFalse(lowerCase.contains("guaranteed"), "No guaranteed-return claim may be made");
        assertFalse(lowerCase.contains("assured return"));
    }
}
