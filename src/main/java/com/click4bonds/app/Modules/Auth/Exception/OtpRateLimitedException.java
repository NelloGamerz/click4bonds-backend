package com.click4bonds.app.Modules.Auth.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when one client address has requested too many OTPs in a window.
 *
 * <p>Distinct from the OTP module's resend cooldown, which is per phone number:
 * this one is per client address and exists because a cooldown alone does not
 * stop a sweep across many numbers. Since every request that gets this far
 * costs an SMS, the limit is also the thing standing between an attacker and
 * the messaging bill.</p>
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class OtpRateLimitedException extends RuntimeException {

    public OtpRateLimitedException() {
        super("Too many verification codes requested. Please try again later.");
    }
}
