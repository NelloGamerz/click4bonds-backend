package com.click4bonds.app.Modules.Analytics.Model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One row of {@code click4bonds_analytics.analytics_events} read back out.
 *
 * <p>Deliberately not {@link AnalyticsEvent}. The write model constrains
 * {@code eventType} to the {@link AnalyticsEventType} enum because a typo must
 * not be able to create a new low-cardinality value; reading has the opposite
 * requirement. A row written by an older build — or by any of the event types
 * currently commented out of that enum — still has to be readable, and
 * {@code valueOf} on an unrecognised name would fail the whole page. The column
 * is therefore carried as the string ClickHouse actually holds, and the enum is
 * used only where it belongs: narrowing a query.</p>
 *
 * @param eventId    the event's unique id
 * @param eventType  the event name as stored, not resolved against the enum
 * @param userId     the acting user
 * @param sessionId  the browser session, empty string when unset
 * @param bondId     the bond in play, or {@code null} for events without one
 * @param eventTime  when the event happened
 * @param source     the channel, for example {@code WEB}
 * @param page       the screen that raised the event
 * @param metadata   the event's extras, parsed from the stored JSON; never
 *                   {@code null}, empty when absent or unparseable
 */
public record AnalyticsEventRow(

        UUID eventId,

        String eventType,

        UUID userId,

        String sessionId,

        UUID bondId,

        Instant eventTime,

        String source,

        String page,

        Map<String, Object> metadata

) {

    /**
     * Takes a defensive, order-preserving copy so the caller cannot mutate the
     * map a response was built from.
     */
    public AnalyticsEventRow {

        metadata = (metadata != null)
                ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata))
                : Map.of();
    }
}
