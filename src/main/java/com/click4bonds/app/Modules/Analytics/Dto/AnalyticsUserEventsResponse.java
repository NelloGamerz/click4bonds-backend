package com.click4bonds.app.Modules.Analytics.Dto;

import java.util.List;
import java.util.UUID;

/**
 * One page of a user's analytics events, with totals for the whole set.
 *
 * <p>Cursor paginated, following the same shape as
 * {@link com.click4bonds.app.Modules.Bond.Dto.IssuerPageResponse}: the caller
 * sends {@link #nextCursor} back as the {@code cursor} query parameter to fetch
 * the following page, and stops when {@link #hasNext} is {@code false}.</p>
 *
 * <p>{@code size} is the number of items in <em>this</em> page, not the page
 * size that was requested — a last page is usually short. It is redundant with
 * {@code items.length} and kept only so a client that reads just the envelope
 * still learns how many rows came back.</p>
 *
 * @param userId     the user these events belong to
 * @param items      the events, newest first; at most the requested page size
 * @param size       how many items this page holds
 * @param hasNext    whether another page exists behind this one
 * @param nextCursor the value to pass back as {@code cursor}, or {@code null}
 *                   when this is the last page
 * @param summary    totals across every event matching the filters, independent
 *                   of which page this is
 */
public record AnalyticsUserEventsResponse(

        UUID userId,

        List<AnalyticsEventResponse> items,

        int size,

        boolean hasNext,

        String nextCursor,

        AnalyticsUserEventsSummary summary

) {
}
