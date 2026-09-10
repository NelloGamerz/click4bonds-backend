package com.click4bonds.app.Modules.Common.Exceptions;

/**
 * Raised when the Redis infrastructure fails (connection lost, timeout,
 * serialization failure, unexpected value type, ...).
 *
 * <p>The underlying driver exception is kept as the cause for diagnostics,
 * but is never surfaced to callers. Keys and values are intentionally left
 * out of the message: both can contain personally identifiable information
 * (email addresses, phone numbers) or secrets.</p>
 */
public class RedisOperationException extends InternalServerException {

    public RedisOperationException(String message) {
        super(message);
    }

    public RedisOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
