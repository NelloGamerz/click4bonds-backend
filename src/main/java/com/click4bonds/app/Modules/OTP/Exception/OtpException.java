package com.click4bonds.app.Modules.OTP.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Base type for every OTP domain failure.
 *
 * <p>Messages are user-safe by construction: they never contain the submitted
 * code, the expected code, its hash, or the identifier.</p>
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class OtpException extends RuntimeException {

    public OtpException(String message) {
        super(message);
    }

    public OtpException(String message, Throwable cause) {
        super(message, cause);
    }
}
