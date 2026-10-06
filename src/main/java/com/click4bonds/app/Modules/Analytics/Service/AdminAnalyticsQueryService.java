package com.click4bonds.app.Modules.Analytics.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsEventResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsEventSearchItem;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsEventSearchResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsSummary;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventRow;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

import lombok.RequiredArgsConstructor;

/**
 * Serves the admin reporting endpoints: one user's analytics events, and a
 * search for events across every user.
 *
 * <p>Sits between the controller and {@link ClickHouseService}. The controller
 * deals in HTTP, {@code ClickHouseService} deals in SQL, and this class owns
 * what neither should: turning a raw row into the response shape, deciding
 * where a page ends, and rejecting a request that cannot be answered.</p>
 *
 * <p>The two endpoints differ only in their filter and their envelope. Both
 * page by cursor over the same ordering, so the arithmetic that decides where a
 * page ends lives once, in {@link EventPage}, and both responses are built from
 * it.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminAnalyticsQueryService {

    /** Page size when the caller does not ask for one. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Largest page a caller may ask for.
     *
     * <p>Capped because every read here is a scan — {@code user_id} is not in
     * the table's sorting key — so the cost of a request is set by how many
     * rows it returns. Without a ceiling, one caller asking for a million rows
     * would spend the analytics store's memory on a page nobody can read.</p>
     */
    public static final int MAX_PAGE_SIZE = 200;

    private final ClickHouseService clickHouseService;

    /**
     * Reads one page of a user's events, newest first, with totals.
     *
     * <p>The page and its totals are two queries against ClickHouse. They are
     * not run in one transaction and do not need to be: the totals describe the
     * filtered set, the page describes a position in it, and an event arriving
     * between the two only means the newest total is one higher than the page
     * implies — correct, if briefly, at a boundary the caller is already
     * crossing.</p>
     *
     * @param userId    the user whose events to read, required
     * @param eventType optional event name to narrow to, {@code null} for all
     * @param from      optional inclusive lower bound on {@code event_time}
     * @param to        optional inclusive upper bound on {@code event_time}
     * @param cursor    the previous page's {@code nextCursor}, or {@code null}
     *                  to start from the newest event
     * @param size      requested page size; {@code 0} or less takes
     *                  {@link #DEFAULT_PAGE_SIZE}
     * @return the page, with totals for the whole filtered set
     * @throws BadRequestException if {@code size} is out of range, {@code from}
     *                             is after {@code to}, or {@code cursor} is not
     *                             a cursor this service issued
     */
    public AnalyticsUserEventsResponse getUserEvents(
            UUID userId,
            AnalyticsEventType eventType,
            Instant from,
            Instant to,
            String cursor,
            int size) {

        int pageSize = resolvePageSize(size);

        validateRange(from, to);

        AnalyticsCursor position = decodeCursor(cursor);

        // One row past the page: its presence is what proves another page
        // exists, without a second query and without a COUNT of the offset
        // we are not using. The extra row is dropped before returning.
        EventPage page = EventPage.of(
                clickHouseService.findUserEvents(
                        userId,
                        eventType,
                        from,
                        to,
                        position,
                        pageSize + 1),
                pageSize);

        return new AnalyticsUserEventsResponse(
                userId,
                page.rows()
                        .stream()
                        .map(AdminAnalyticsQueryService::toResponse)
                        .toList(),
                page.rows().size(),
                page.hasNext(),
                page.nextCursor(),
                summary(userId, eventType, from, to));
    }

    /**
     * Searches every user's events, newest first, with totals left out.
     *
     * <p>The same page of the same table as {@link #getUserEvents}, minus the
     * user filter. Neither endpoint is a subset of the other: a search cannot be
     * answered by the per-user read, because the caller does not know whose
     * events to ask for.</p>
     *
     * <p>Reads are not run in one transaction, and do not need to be — see
     * {@link #getUserEvents}. A row inserted mid-search shifts the newest page
     * and nothing else, because the cursor names a position in the ordering
     * rather than an offset into it.</p>
     *
     * @param eventType optional event name to narrow to, {@code null} for all;
     *                  naming one reads a range of the sorting key rather than
     *                  the whole partition
     * @param from      optional inclusive lower bound on {@code event_time}
     * @param to        optional inclusive upper bound on {@code event_time}
     * @param cursor    the previous page's {@code nextCursor}, or {@code null}
     *                  to start from the newest event
     * @param size      requested page size; {@code 0} or less takes
     *                  {@link #DEFAULT_PAGE_SIZE}
     * @return the page of matching events, across all users
     * @throws BadRequestException if {@code size} is out of range, {@code from}
     *                             is after {@code to}, or {@code cursor} is not
     *                             a cursor this service issued
     */
    public AnalyticsEventSearchResponse searchEvents(
            AnalyticsEventType eventType,
            Instant from,
            Instant to,
            String cursor,
            int size) {

        int pageSize = resolvePageSize(size);

        validateRange(from, to);

        AnalyticsCursor position = decodeCursor(cursor);

        EventPage page = EventPage.of(
                clickHouseService.findAllEvents(
                        eventType,
                        from,
                        to,
                        position,
                        pageSize + 1),
                pageSize);

        return new AnalyticsEventSearchResponse(
                page.rows()
                        .stream()
                        .map(AdminAnalyticsQueryService::toSearchItem)
                        .toList(),
                page.rows().size(),
                page.hasNext(),
                page.nextCursor());
    }

    /**
     * @throws BadRequestException if {@code from} is set and falls after
     *                             {@code to}
     */
    private static void validateRange(Instant from, Instant to) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("The 'from' time must not be after the 'to' time.");
        }
    }

    /**
     * @param cursor the raw {@code cursor} query parameter
     * @return the decoded position, or {@code null} for the first page — a
     *         cursor that was sent but blank counts as omitted
     * @throws BadRequestException if the token is not a cursor this service issued
     */
    private static AnalyticsCursor decodeCursor(String cursor) {

        return (cursor != null && !cursor.isBlank())
                ? AnalyticsCursor.decode(cursor)
                : null;
    }

    /**
     * Builds the totals block for the filtered set.
     *
     * <p>{@code totalEvents} is summed here rather than read from its own
     * {@code count()} query: the per-type counts already account for every
     * matching row, so a separate total would be a third scan of the same data
     * and one more thing that could disagree with the breakdown.</p>
     */
    private AnalyticsUserEventsSummary summary(
            UUID userId,
            AnalyticsEventType eventType,
            Instant from,
            Instant to) {

        Map<String, Long> eventsByType = clickHouseService.summariseUserEvents(
                userId,
                eventType,
                from,
                to);

        long totalEvents = eventsByType.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        return new AnalyticsUserEventsSummary(totalEvents, eventsByType);
    }

    /**
     * One page of rows, cut from the rows a read returned.
     *
     * <p>Both endpoints read one row more than the page they intend to return,
     * so the presence of that row is what answers "is there another page" —
     * without a second query and without a {@code COUNT} of an offset nobody
     * uses. Cutting the look-ahead row off, and naming the last row that
     * survives, is the whole of the paging arithmetic; it lives here so the two
     * endpoints cannot disagree about where a page ends.</p>
     *
     * @param rows       this page's rows, the look-ahead row already dropped
     * @param hasNext    whether the look-ahead row arrived
     * @param nextCursor the cursor to resume at, or {@code null} on the last page
     */
    private record EventPage(List<AnalyticsEventRow> rows, boolean hasNext, String nextCursor) {

        /**
         * @param fetched  the rows ClickHouse returned, at most one more than
         *                 the page size
         * @param pageSize how many rows the page should hold
         */
        private static EventPage of(List<AnalyticsEventRow> fetched, int pageSize) {

            boolean hasNext = fetched.size() > pageSize;

            List<AnalyticsEventRow> page = hasNext
                    ? fetched.subList(0, pageSize)
                    : fetched;

            return new EventPage(page, hasNext, hasNext ? cursorFor(page) : null);
        }
    }

    /**
     * @return the cursor naming the last row of {@code page}, so the next call
     *         resumes immediately after it
     */
    private static String cursorFor(List<AnalyticsEventRow> page) {

        AnalyticsEventRow last = page.get(page.size() - 1);

        return new AnalyticsCursor(last.eventTime(), last.eventId()).encode();
    }

    /**
     * @param size the requested page size
     * @return {@code size}, or {@link #DEFAULT_PAGE_SIZE} when it was not set
     * @throws BadRequestException if it exceeds {@link #MAX_PAGE_SIZE}
     */
    private static int resolvePageSize(int size) {

        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        if (size > MAX_PAGE_SIZE) {
            // Rejected rather than clamped: a caller that asked for 5000 rows
            // and silently received 200 would page on a size the server never
            // honoured, and read the missing rows as data that does not exist.
            throw new BadRequestException(
                    "The page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }

        return size;
    }

    /** Maps a stored row onto the response shape. */
    private static AnalyticsEventResponse toResponse(AnalyticsEventRow row) {

        return new AnalyticsEventResponse(
                row.eventId(),
                row.eventType(),
                row.sessionId(),
                row.bondId(),
                row.eventTime(),
                row.source(),
                row.page(),
                row.metadata());
    }

    /**
     * Maps a stored row onto the search item shape.
     *
     * <p>Carries {@code userId}, which the per-user response leaves to its
     * envelope — a page here is not scoped to one person, so each row has to say
     * whose it is.</p>
     */
    private static AnalyticsEventSearchItem toSearchItem(AnalyticsEventRow row) {

        return new AnalyticsEventSearchItem(
                row.eventId(),
                row.eventType(),
                row.userId(),
                row.sessionId(),
                row.bondId(),
                row.eventTime(),
                row.source(),
                row.page(),
                row.metadata());
    }
}
