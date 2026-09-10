package com.click4bonds.app.Modules.Email.Exception;

/**
 * Raised when the configured {@code email.provider} cannot be resolved to an
 * available {@code EmailProvider} implementation.
 *
 * <p>This is a configuration error: it is thrown while the application context
 * starts up so that a misconfigured deployment fails fast instead of failing
 * on the first email that has to be sent.</p>
 */
public class UnsupportedEmailProviderException extends RuntimeException {

    public UnsupportedEmailProviderException(String message) {
        super(message);
    }
}
