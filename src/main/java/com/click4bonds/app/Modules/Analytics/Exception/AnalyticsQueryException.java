package com.click4bonds.app.Modules.Analytics.Exception;

/**
 * Raised when analytics events could not be read back out of ClickHouse.
 *
 * <p>Distinct from {@link AnalyticsStorageException}, which is thrown on the
 * write path and drives the Kafka consumer's retry. A read failure has no retry
 * to feed and no batch to lose — it is a request that could not be served, and
 * the caller is told the analytics store is unavailable rather than that their
 * request was wrong.</p>
 */
public class AnalyticsQueryException extends RuntimeException {

    public AnalyticsQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
