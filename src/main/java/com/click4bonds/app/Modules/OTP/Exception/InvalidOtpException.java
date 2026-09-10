package com.click4bonds.app.Modules.OTP.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a submitted OTP is wrong, unknown, or expired.
 *
 * <p>All three cases share one message and one type on purpose: telling a
 * caller which of them happened would leak whether an OTP is currently
 * outstanding for the identifier.</p>
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidOtpException extends OtpException {

    public InvalidOtpException() {
        super("Invalid or expired OTP");
    }
}
