package com.click4bonds.app.Modules.Analytics.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsEventResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsSummary;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventRow;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

import lombok.RequiredArgsConstructor;

/**
 * Serves the admin reporting endpoint: one user's analytics events, paged.
 *
 * <p>Sits between the controller and {@link ClickHouseService}. The controller
 * deals in HTTP, {@code ClickHouseService} deals in SQL, and this class owns
 * what neither should: turning a raw row into the response shape, deciding
 * where a page ends, and rejecting a request that cannot be answered.</p>
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

        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("The 'from' time must not be after the 'to' time.");
        }

        AnalyticsCursor position = (cursor != null && !cursor.isBlank())
                ? AnalyticsCursor.decode(cursor)
                : null;

        // One row past the page: its presence is what proves another page
        // exists, without a second query and without a COUNT of the offset
        // we are not using. The extra row is dropped before returning.
        List<AnalyticsEventRow> rows = clickHouseService.findUserEvents(
                userId,
                eventType,
                from,
                to,
                position,
                pageSize + 1);

        boolean hasNext = rows.size() > pageSize;

        List<AnalyticsEventRow> page = hasNext
                ? rows.subList(0, pageSize)
                : rows;

        return new AnalyticsUserEventsResponse(
                userId,
                page.stream()
                        .map(AdminAnalyticsQueryService::toResponse)
                        .toList(),
                page.size(),
                hasNext,
                hasNext ? cursorFor(page) : null,
                summary(userId, eventType, from, to));
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
}
