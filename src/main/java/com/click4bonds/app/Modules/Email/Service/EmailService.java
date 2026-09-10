package com.click4bonds.app.Modules.Email.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Email.Model.EmailRequest;
import com.click4bonds.app.Modules.Email.Provider.EmailProvider;
import com.click4bonds.app.Modules.Email.Provider.EmailProviderFactory;
import com.click4bonds.app.Modules.Email.Template.OtpEmailTemplate;
import com.click4bonds.app.Modules.Email.Template.WelcomeEmailTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Application facing entry point for sending email.
 *
 * <p>Callers express the use case ({@link #sendOtp}, {@link #sendWelcomeEmail})
 * and this service takes care of the subject, the template and the delivery
 * provider. Nothing outside the email module needs to know which provider is
 * configured.</p>
 *
 * <p>Sending is synchronous: it keeps OTP delivery behaviour predictable, and
 * the project has no existing asynchronous mail convention. The call blocks
 * for one HTTP round trip to the provider.</p>
 */
@Service
@Slf4j
public class EmailService {

    private final EmailProviderFactory emailProviderFactory;
    private final String loginUrl;

    public EmailService(
            EmailProviderFactory emailProviderFactory,
            @Value("${frontend.url:}") String loginUrl) {

        this.emailProviderFactory = emailProviderFactory;
        this.loginUrl = loginUrl;
    }

    /**
     * Sends the email verification code using the default validity.
     *
     * @param to  recipient address
     * @param otp one-time code
     */
    public void sendOtp(String to, String otp) {
        sendOtp(to, otp, OtpEmailTemplate.DEFAULT_EXPIRY_MINUTES);
    }

    /**
     * Sends the email verification code.
     *
     * @param to            recipient address
     * @param otp           one-time code
     * @param expiryMinutes how long the code stays valid
     */
    public void sendOtp(String to, String otp, int expiryMinutes) {

        send(new EmailRequest(to, OtpEmailTemplate.SUBJECT, OtpEmailTemplate.render(otp, expiryMinutes)));

        // The code itself is never logged.
        log.info("OTP email sent to {}", to);
    }

    /**
     * Sends the welcome email after an account has been created.
     *
     * @param to       recipient address
     * @param userName name used in the greeting; may be missing
     */
    public void sendWelcomeEmail(String to, String userName) {

        send(new EmailRequest(to, WelcomeEmailTemplate.SUBJECT, WelcomeEmailTemplate.render(userName, loginUrl)));

        log.info("Welcome email sent to {}", to);
    }

    private void send(EmailRequest request) {
        EmailProvider provider = emailProviderFactory.getProvider();
        provider.send(request);
    }
}
