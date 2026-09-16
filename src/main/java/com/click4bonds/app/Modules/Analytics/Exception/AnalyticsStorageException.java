package com.click4bonds.app.Modules.Analytics.Exception;

/**
 * Raised when a batch of analytics events could not be written to ClickHouse.
 *
 * <p>Unchecked on purpose: analytics must never fail a business request. The
 * exception is thrown on the Kafka consumer thread and caught by
 * {@code AnalyticsBatchService}, which puts the affected events back into the
 * buffer for the next flush.</p>
 */
public class AnalyticsStorageException extends RuntimeException {

    public AnalyticsStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
