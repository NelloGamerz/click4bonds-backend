package com.click4bonds.app.Modules.Analytics.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Producer.AnalyticsEventProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The analytics module's public entry point, and the only analytics class a
 * business service should ever import.
 *
 * <p>A caller says what happened. It does not know that the event is JSON,
 * that it travels over {@code click4bonds.analytics}, that a consumer batches
 * it, or that it lands in ClickHouse. Those are this module's business: a
 * business service that named a topic or wrote a database insert would have to
 * be changed every time the pipeline changes.</p>
 *
 * <p>Calling {@code track} is fire-and-forget. It returns as soon as the event
 * has been handed to the Kafka producer, which buffers and sends it on its own
 * thread, so the business request does not wait for Kafka or ClickHouse.</p>
 *
 * <p>Analytics is never allowed to break the caller. A failure to publish is
 * logged and swallowed here rather than propagated, because a bond page must
 * still render when the analytics pipeline is down. The consequence is that a
 * dropped event is silent to the user and visible only in the logs.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AnalyticsEventProducer producer;

    /**
     * Records that something happened.
     *
     * <p>Builds the event — generating its id and timestamp — and publishes it.
     * Call it after the business operation has succeeded, not before: an event
     * for an operation that then failed is worse than no event.</p>
     *
     * @param eventType what happened; required. A {@code null} type is logged
     *                  and skipped, since it would carry no information
     * @param userId    the signed-in user, or {@code null} for anonymous traffic
     * @param sessionId the browser session, or {@code null} when there is none
     * @param bondId    the bond involved, or {@code null} for events without one
     * @param source    the channel, for example {@code "WEB"} or {@code "MOBILE"};
     *                  {@code null} becomes an empty string rather than failing
     *                  the insert later
     * @param page      the screen that raised the event, for example
     *                  {@code "BOND_DETAILS"}; {@code null} becomes empty
     * @param metadata  event-specific extras that do not deserve their own
     *                  column, for example
     *                  {@code Map.of("filter", "AAA")}; {@code null} becomes
     *                  an empty map
     */
    public void track(
            AnalyticsEventType eventType,
            UUID userId,
            String sessionId,
            UUID bondId,
            String source,
            String page,
            Map<String, Object> metadata) {

        if (eventType == null) {
            log.warn("Ignored an analytics event with no event type (userId={}, page={})",
                    userId, page);
            return;
        }

        AnalyticsEvent event = new AnalyticsEvent(
                UUID.randomUUID(),
                eventType,
                userId,
                sessionId,
                bondId,
                Instant.now(),
                source,
                page,
                metadata);

        try {

            producer.publish(event);

        } catch (RuntimeException e) {

            log.error("Could not publish analytics event {} of type {}",
                    event.eventId(), eventType, e);
        }
    }
}
