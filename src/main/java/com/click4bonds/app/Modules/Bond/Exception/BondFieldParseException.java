package com.click4bonds.app.Modules.Bond.Exception;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * Raised when a value supplied to a bond create/update request cannot be
 * interpreted.
 *
 * <p>
 * The admin API accepts loosely-typed values — {@code "8.45%"}, {@code
 * "Secured"}, {@code "7/Mar/28"}, {@code "1.50 Lakh"}, {@code "10 Lacs Lot"} —
 * and normalizes them before they reach the entity. When one of those values
 * cannot be parsed the whole request is rejected, because storing a bond whose
 * normalized columns silently disagree with its raw text is worse than making
 * the caller fix the row.
 *
 * <p>
 * Extends {@link BadRequestException}, so the existing
 * {@code GlobalExceptionHandler} maps it to {@code 400 BAD_REQUEST} without any
 * change to the advice.
 */
public class BondFieldParseException extends BadRequestException {

    private final String field;

    public BondFieldParseException(String field, String value, String reason) {
        super("Could not parse '" + field + "' from value '" + value + "': " + reason);
        this.field = field;
    }

    public BondFieldParseException(String field, String value, String reason, Throwable cause) {
        super("Could not parse '" + field + "' from value '" + value + "': " + reason);
        this.field = field;
        initCause(cause);
    }

    /**
     * The request field the rejected value came from, e.g. {@code couponRate}.
     */
    public String getField() {
        return field;
    }
}
