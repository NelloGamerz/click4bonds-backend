package com.click4bonds.app.Modules.Analytics.Model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One analytics event travelling from a business service to ClickHouse.
 *
 * <p>The shape mirrors the {@code click4bonds_analytics.analytics_events} table
 * one field per column, so the consumer can insert a batch without any further
 * mapping. Types were chosen for both hops:</p>
 *
 * <ul>
 *   <li>{@link UUID} for the event id — the ClickHouse column is {@code UUID},
 *       and the JDBC driver accepts the object directly.</li>
 *   <li>{@link Long} for {@code user_id} and {@code bond_id}, both nullable to
 *       match the {@code Nullable(UInt64)} columns; anonymous traffic and
 *       non-bond events leave them unset.</li>
 *   <li>{@link Instant} for the time — JSON-serializes through the JavaTime
 *       module and becomes {@code DateTime64(3)} on insert.</li>
 *   <li>{@link AnalyticsEventType} rather than a free string, so the set of
 *       event types is closed at compile time and a typo cannot create a new
 *       low-cardinality value in ClickHouse.</li>
 * </ul>
 *
 * <p>{@code eventType}, {@code source} and {@code page} map to
 * {@code LowCardinality(String)} columns. They are intentionally unconstrained
 * strings: forcing a page or source enum would mean editing this module every
 * time the frontend adds a screen.</p>
 *
 * <p>Records are immutable and this one is passed between threads (producer
 * thread, Kafka listener thread, scheduler thread), so it holds no mutable
 * state beyond {@code metadata}.</p>
 *
 * @param eventId    unique id of this event, also used as the Kafka record key
 * @param eventType  what happened
 * @param userId     the acting user, or {@code null} when not signed in
 * @param sessionId  the browser session, or {@code null} when there is none
 * @param bondId     the bond in play, or {@code null} for events without one
 * @param eventTime  when the event happened, set by {@code AnalyticsService}
 * @param source     the channel, for example {@code WEB} or {@code MOBILE}
 * @param page       the screen that raised the event
 * @param metadata   event-specific extras; never {@code null}, may be empty
 */
public record AnalyticsEvent(

        UUID eventId,

        AnalyticsEventType eventType,

        Long userId,

        String sessionId,

        Long bondId,

        Instant eventTime,

        String source,

        String page,

        Map<String, Object> metadata

) {

    /**
     * Replaces a {@code null} metadata map with an empty one, and takes a
     * defensive copy otherwise.
     *
     * <p>Callers that have nothing extra to report should not have to choose
     * between passing {@code null} and {@code Map.of()}, and the two
     * downstream steps — JSON encoding for Kafka and JSON encoding for the
     * {@code metadata} column — would otherwise each need their own null
     * branch. The copy matters because the map is written on the Kafka
     * consumer thread long after the business service returned, so a caller
     * mutating its map afterwards must not be able to change the event.</p>
     *
     * <p>The copy preserves iteration order and tolerates {@code null} values.
     * {@link Map#copyOf} would do neither: it rejects null values — a business
     * service must not fail because of an analytics payload — and leaves the
     * JSON field order unspecified.</p>
     */
    public AnalyticsEvent {

        metadata = (metadata != null)
                ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata))
                : Map.of();
    }
}
