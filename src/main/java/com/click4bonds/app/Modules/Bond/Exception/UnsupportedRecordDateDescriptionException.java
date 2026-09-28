package com.click4bonds.app.Modules.Bond.Exception;

/**
 * Thrown when a bond's record-date description cannot be interpreted.
 *
 * <p>
 * This is intentionally a hard failure rather than a silent fallback. A record
 * date drives coupon entitlement, and coupon entitlement drives the cash flows
 * fed to {@code XirrCalculator}. Guessing a default offset (for example
 * "15 days prior") would silently produce a plausible-looking but wrong YTM,
 * which is far worse than refusing to calculate.
 *
 * <p>
 * The message always contains the original description as received from the
 * source so that the value can be corrected at the source.
 */
public class UnsupportedRecordDateDescriptionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String description;

    public UnsupportedRecordDateDescriptionException(String description) {
        super("Unsupported record date description: " + description);
        this.description = description;
    }

    /**
     * @return the original, unmodified description that could not be parsed
     */
    public String getDescription() {
        return description;
    }
}
