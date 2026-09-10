package com.click4bonds.app.Modules.OTP.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised once the configured maximum number of failed verifications is
 * reached. The OTP is destroyed at the same time, so the caller must request
 * a new one.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class OtpMaxAttemptsExceededException extends OtpException {

    public OtpMaxAttemptsExceededException() {
        super("Too many invalid attempts. Please request a new OTP");
    }
}
