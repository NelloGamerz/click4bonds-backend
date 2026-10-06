package com.click4bonds.app.Modules.Analytics.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * The position a page of events stopped at, so the next page can resume there.
 *
 * <p>Holds both the timestamp and the event id, not the timestamp alone. The
 * {@code analytics_events} sorting key is {@code (event_type, event_time)}, and
 * {@code event_time} is {@code DateTime64(3)} — millisecond resolution. Events
 * raised in one batch routinely share a millisecond, so paging on the timestamp
 * alone would either skip the tied rows or repeat them, depending on which side
 * of the boundary the comparison lands. Adding the id makes the ordering total
 * and the page boundary unambiguous.</p>
 *
 * <p>The encoding is deliberately opaque. It is base64url so a caller cannot
 * read a cursor and be tempted to construct one, and so that {@code nextCursor}
 * survives being put in a query string unescaped — no padding, and no
 * {@code +} or {@code /} to be re-encoded.</p>
 *
 * @param eventTime the {@code event_time} of the last row on the page
 * @param eventId   the {@code event_id} of that same row
 */
public record AnalyticsCursor(Instant eventTime, UUID eventId) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private static final char SEPARATOR = ':';

    /**
     * Renders this position as the {@code nextCursor} value a caller sends back.
     *
     * <p>Milliseconds are what is written, because that is exactly the precision
     * ClickHouse stores for {@code DateTime64(3)} — a cursor therefore round-trips
     * to the identical instant and cannot land between two rows.</p>
     *
     * @return a URL-safe token, never {@code null}
     */
    public String encode() {
        return ENCODER.encodeToString(
                (eventTime.toEpochMilli() + String.valueOf(SEPARATOR) + eventId)
                        .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses a cursor previously produced by {@link #encode()}.
     *
     * <p>A malformed cursor is the caller's mistake, not a server fault, so it
     * is reported as {@link BadRequestException} — the alternative, ignoring it
     * and returning the first page, would look like the pagination had silently
     * restarted.</p>
     *
     * @param cursor the raw {@code cursor} query parameter
     * @return the decoded position
     * @throws BadRequestException if the token is not a cursor this class wrote
     */
    public static AnalyticsCursor decode(String cursor) {

        try {
            String raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);

            int separator = raw.indexOf(SEPARATOR);

            if (separator < 1 || separator == raw.length() - 1) {
                throw new IllegalArgumentException("cursor has no separator");
            }

            return new AnalyticsCursor(
                    Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
                    UUID.fromString(raw.substring(separator + 1)));

        } catch (RuntimeException e) {

            throw new BadRequestException(
                    "The cursor is not valid. Send back the nextCursor from the previous page, or omit it to start from the newest events.");
        }
    }
}
