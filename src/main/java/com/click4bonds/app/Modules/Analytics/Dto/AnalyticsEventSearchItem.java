package com.click4bonds.app.Modules.Analytics.Dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One analytics event in a cross-user search, with the user it belongs to.
 *
 * <p>{@link AnalyticsEventResponse} deliberately omits {@code userId} because
 * every row on a page there belongs to the user named in the envelope. A search
 * has no such envelope — the rows on one page belong to different people — so
 * the id has to travel on each item or the administrator cannot tell whose
 * activity they are reading.</p>
 *
 * <p>{@code eventType} is a string rather than
 * {@link com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType}, for
 * the same reason as on {@link AnalyticsEventResponse}: rows written under an
 * event name since removed from that enum are still readable, and binding to the
 * enum would make them unrepresentable.</p>
 *
 * @param eventId    the event's unique id
 * @param eventType  the event name as stored
 * @param userId     the user who raised it, {@code null} for anonymous events
 * @param sessionId  the browser session, empty string when unset
 * @param bondId     the bond in play, or {@code null} for events without one
 * @param eventTime  when the event happened, UTC
 * @param source     the channel, for example {@code WEB}
 * @param page       the screen that raised the event
 * @param metadata   the event's extras; never {@code null}, may be empty
 */
public record AnalyticsEventSearchItem(

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
}
