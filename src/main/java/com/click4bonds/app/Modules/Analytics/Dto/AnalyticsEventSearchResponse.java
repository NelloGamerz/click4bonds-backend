package com.click4bonds.app.Modules.Analytics.Dto;

import java.util.List;

/**
 * One page of analytics events matching a search, across every user.
 *
 * <p>Cursor paginated on the same terms as
 * {@link AnalyticsUserEventsResponse}: the caller sends {@link #nextCursor} back
 * as the {@code cursor} query parameter to fetch the following page, and stops
 * when {@link #hasNext} is {@code false}.</p>
 *
 * <p>There is no {@code userId} on the envelope — the page is not scoped to one
 * person — and no summary block. The summary on the per-user response describes
 * a set an administrator has already decided to look at; here the question is
 * "which rows match", and counting every match across every user would put a
 * second, unbounded scan behind a request that is only meant to return a page.
 * A caller wanting totals narrows with a filter and reads them from the
 * per-user endpoint, or asks for a narrower search.</p>
 *
 * <p>{@code size} is the number of items in <em>this</em> page, not the page
 * size that was requested — a last page is usually short.</p>
 *
 * @param items      the events, newest first; at most the requested page size
 * @param size       how many items this page holds
 * @param hasNext    whether another page exists behind this one
 * @param nextCursor the value to pass back as {@code cursor}, or {@code null}
 *                   when this is the last page
 */
public record AnalyticsEventSearchResponse(

        List<AnalyticsEventSearchItem> items,

        int size,

        boolean hasNext,

        String nextCursor

) {
}
