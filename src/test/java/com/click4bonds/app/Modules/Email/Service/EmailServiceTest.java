// package com.click4bonds.app.Modules.Email.Service;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertFalse;
// import static org.junit.jupiter.api.Assertions.assertThrows;
// import static org.junit.jupiter.api.Assertions.assertTrue;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.doThrow;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.mockito.ArgumentCaptor;

// import com.click4bonds.app.Modules.Email.Exception.EmailSendException;
// import com.click4bonds.app.Modules.Email.Model.EmailRequest;
// import com.click4bonds.app.Modules.Email.Provider.EmailProvider;
// import com.click4bonds.app.Modules.Email.Provider.EmailProviderFactory;
// import com.click4bonds.app.Modules.Email.Template.OtpEmailTemplate;
// import com.click4bonds.app.Modules.Email.Template.WelcomeEmailTemplate;

// class EmailServiceTest {

//     private static final String LOGIN_URL = "https://app.click4bonds.com";

//     private EmailProvider provider;
//     private EmailService emailService;

//     @BeforeEach
//     void setUp() {

//         provider = mock(EmailProvider.class);

//         EmailProviderFactory factory = mock(EmailProviderFactory.class);
//         when(factory.getProvider()).thenReturn(provider);

//         emailService = new EmailService(factory, LOGIN_URL);
//     }

//     @Test
//     void shouldSendOtpThroughTheSelectedProvider() {

//         emailService.sendOtp("user@example.com", "483920");

//         EmailRequest sent = captureSent();

//         assertEquals("user@example.com", sent.to());
//         assertEquals(OtpEmailTemplate.SUBJECT, sent.subject());
//         assertTrue(sent.html().contains("483920"), "The code must reach the email body");
//         assertTrue(sent.html().contains(OtpEmailTemplate.DEFAULT_EXPIRY_MINUTES + " minutes"));
//     }

//     @Test
//     void shouldHonourCustomOtpExpiry() {

//         emailService.sendOtp("user@example.com", "483920", 5);

//         assertTrue(captureSent().html().contains("5 minutes"));
//     }

//     @Test
//     void shouldSendWelcomeEmailThroughTheSelectedProvider() {

//         emailService.sendWelcomeEmail("user@example.com", "Karan");

//         EmailRequest sent = captureSent();

//         assertEquals("user@example.com", sent.to());
//         assertEquals(WelcomeEmailTemplate.SUBJECT, sent.subject());
//         assertTrue(sent.html().contains("Karan"));
//         assertTrue(sent.html().contains(LOGIN_URL));
//     }

//     @Test
//     void shouldPropagateProviderFailuresToTheCaller() {

//         doThrow(new EmailSendException("resend unavailable"))
//                 .when(provider).send(any(EmailRequest.class));

//         assertThrows(EmailSendException.class, () -> emailService.sendOtp("user@example.com", "483920"));
//     }

//     @Test
//     void shouldNotLeakOtpToTheWelcomeEmail() {

//         emailService.sendWelcomeEmail("user@example.com", "Karan");

//         assertFalse(captureSent().html().contains("{{"));
//     }

//     private EmailRequest captureSent() {

//         ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
//         verify(provider).send(captor.capture());
//         return captor.getValue();
//     }
// }


package com.click4bonds.app.Modules.Email.Service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EmailServiceTest {

    private static final String TEST_EMAIL = "karanpareek1112@gmail.com";

    @Autowired
    private EmailService emailService;

    @Test
    void shouldSendRealOtpEmail() {

        assertDoesNotThrow(() ->
                emailService.sendOtp(TEST_EMAIL, "483920")
        );
    }

    @Test
    void shouldSendRealWelcomeEmail() {

        assertDoesNotThrow(() ->
                emailService.sendWelcomeEmail(TEST_EMAIL, "Karan")
        );
    }
}
