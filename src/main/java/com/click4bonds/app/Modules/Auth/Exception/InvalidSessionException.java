package com.click4bonds.app.Modules.Auth.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a session cannot be resolved from a request.
 *
 * <p>One type covers every way that happens — the identifier is absent,
 * unknown, expired, already rotated out, or revoked by a logout. Distinguishing
 * them would tell a caller whether a session exists, which is exactly what
 * someone probing a stolen cookie wants to know.</p>
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Your session is no longer valid. Please sign in again.");
    }
}
