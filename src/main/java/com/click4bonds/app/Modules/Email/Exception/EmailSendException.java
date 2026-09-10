package com.click4bonds.app.Modules.Email.Exception;

/**
 * Raised when an email could not be handed over to the configured provider.
 *
 * <p>Provider specific failures (for example a Resend SDK exception) are
 * wrapped in this type so that callers and controllers never depend on a
 * concrete provider's exception hierarchy.</p>
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
