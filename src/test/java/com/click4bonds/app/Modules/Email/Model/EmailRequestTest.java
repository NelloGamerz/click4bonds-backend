package com.click4bonds.app.Modules.Email.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EmailRequestTest {

    @Test
    void shouldBuildValidRequest() {

        EmailRequest request = new EmailRequest("user@example.com", "Subject", "<p>body</p>");

        assertEquals("user@example.com", request.to());
        assertEquals("Subject", request.subject());
        assertEquals("<p>body</p>", request.html());
        assertTrue(request.attachments().isEmpty(), "An email with no attachments carries none");
    }

    @Test
    void shouldCarryAttachments() {

        EmailAttachment attachment =
                new EmailAttachment("letter.pdf", "application/pdf", "pdf bytes".getBytes());

        EmailRequest request = new EmailRequest(
                "user@example.com", "Subject", "<p>body</p>", List.of(attachment));

        assertEquals(List.of(attachment), request.attachments());
    }

    @Test
    void shouldRejectAnIncompleteAttachment() {

        assertThrows(IllegalArgumentException.class,
                () -> new EmailAttachment(null, "application/pdf", "pdf".getBytes()));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailAttachment("letter.pdf", " ", "pdf".getBytes()));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailAttachment("letter.pdf", "application/pdf", new byte[0]));
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
