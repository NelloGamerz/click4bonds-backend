package com.click4bonds.app.Modules.Analytics.Dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One analytics event as returned to an administrator.
 *
 * <p>{@code userId} is not repeated here — every row on a page belongs to the
 * user named in the envelope, so carrying it on each item would be twenty
 * copies of one value.</p>
 *
 * <p>{@code eventType} is a string rather than {@link com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType}.
 * Rows written by an earlier build, or under any of the event names since
 * commented out of that enum, are still readable; binding the response to the
 * enum would make them unrepresentable and fail the mapping.</p>
 *
 * @param eventId    the event's unique id
 * @param eventType  the event name as stored
 * @param sessionId  the browser session, empty string when unset
 * @param bondId     the bond in play, or {@code null} for events without one
 * @param eventTime  when the event happened, UTC
 * @param source     the channel, for example {@code WEB}
 * @param page       the screen that raised the event
 * @param metadata   the event's extras; never {@code null}, may be empty
 */
public record AnalyticsEventResponse(

        UUID eventId,

        String eventType,

        String sessionId,

        UUID bondId,

        Instant eventTime,

        String source,

        String page,

        Map<String, Object> metadata

) {
}
