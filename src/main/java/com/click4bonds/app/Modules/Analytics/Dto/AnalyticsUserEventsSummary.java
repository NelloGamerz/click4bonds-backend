package com.click4bonds.app.Modules.Analytics.Dto;

import java.util.Map;

/**
 * Totals for the whole filtered set, not for the page returned alongside them.
 *
 * <p>These stay constant as an administrator pages forward. A context block
 * that changed on every page would be read as the totals growing as the caller
 * scrolled, which is the opposite of what they are for.</p>
 *
 * <p>An empty {@code eventsByType} means the user has no events matching the
 * filters — it is not a sign of failure. ClickHouse being unreachable is
 * answered with {@code 503} instead, so nothing in this object encodes an
 * error.</p>
 *
 * @param totalEvents  every matching event, the sum of {@code eventsByType}
 * @param eventsByType event name to count, most frequent first; empty when
 *                     nothing matched
 */
public record AnalyticsUserEventsSummary(

        long totalEvents,

        Map<String, Long> eventsByType

) {
}
