package com.click4bonds.app.Modules.Email.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmailRequestTest {

    @Test
    void shouldBuildValidRequest() {

        EmailRequest request = new EmailRequest("user@example.com", "Subject", "<p>body</p>");

        assertEquals("user@example.com", request.to());
        assertEquals("Subject", request.subject());
        assertEquals("<p>body</p>", request.html());
    }

    @Test
    void shouldRejectIncompleteRequests() {

        assertThrows(IllegalArgumentException.class,
                () -> new EmailRequest(null, "Subject", "<p>body</p>"));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailRequest("user@example.com", " ", "<p>body</p>"));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailRequest("user@example.com", "Subject", null));
    }
}
