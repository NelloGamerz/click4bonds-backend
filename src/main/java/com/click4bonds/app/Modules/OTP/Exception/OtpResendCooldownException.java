package com.click4bonds.app.Modules.OTP.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when an OTP is requested again before the resend cooldown elapsed.
 *
 * <p>The outstanding OTP is left untouched, so a rejected resend never
 * extends or shortens the life of the code already delivered.</p>
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class OtpResendCooldownException extends OtpException {

    public OtpResendCooldownException() {
        super("Please wait before requesting a new OTP");
    }
}
