package com.click4bonds.app.Modules.Analytics.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsEventSearchResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsResponse;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventRow;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * Where a page ends and what the caller is told about it.
 *
 * <p>{@link ClickHouseService} is mocked, so these tests describe the paging
 * arithmetic without a ClickHouse server: how many rows are requested, which
 * one becomes the cursor, and when the caller is told there is more.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminAnalyticsQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private ClickHouseService clickHouseService;

    @InjectMocks
    private AdminAnalyticsQueryService service;

    @Test
    void shouldAskClickHouseForOneRowMoreThanThePageSize() {

        stubEvents(rows(3));

        service.getUserEvents(USER_ID, null, null, null, null, 2);

        // The extra row is how hasNext is decided, without a COUNT.
        verify(clickHouseService).findUserEvents(
                eq(USER_ID), isNull(), isNull(), isNull(), isNull(), eq(3));
    }

    @Test
    void shouldReturnOnlyThePageSizeEvenThoughOneMoreWasFetched() {

        stubEvents(rows(3));

        AnalyticsUserEventsResponse response = service.getUserEvents(
                USER_ID, null, null, null, null, 2);

        assertEquals(2, response.items().size(),
                "The look-ahead row must not reach the caller");
        assertEquals(2, response.size());
    }

    @Test
    void shouldReportAnotherPageWhenTheLookAheadRowArrived() {

        stubEvents(rows(3));

        AnalyticsUserEventsResponse response = service.getUserEvents(
                USER_ID, null, null, null, null, 2);

        assertTrue(response.hasNext());
        assertNotNull(response.nextCursor());
    }

    @Test
    void shouldReportTheLastPageWhenNoLookAheadRowArrived() {

        stubEvents(rows(2));

        AnalyticsUserEventsResponse response = service.getUserEvents(
                USER_ID, null, null, null, null, 2);

        assertFalse(response.hasNext());
        assertNull(response.nextCursor(), "A last page must not offer a cursor");
    }

    @Test
    void shouldTreatAnExactlyFullPageAsTheLastOne() {

        // Fetching size + 1 is what distinguishes "full page, more to come"
        // from "full page, and that is all there is".
        stubEvents(rows(2));

        assertFalse(service.getUserEvents(USER_ID, null, null, null, null, 2).hasNext());
    }

    @Test
    void shouldPointTheCursorAtTheLastRowOfThePage() {

        List<AnalyticsEventRow> rows = rows(3);
        stubEvents(rows);

        AnalyticsUserEventsResponse response = service.getUserEvents(
                USER_ID, null, null, null, null, 2);

        AnalyticsCursor cursor = AnalyticsCursor.decode(response.nextCursor());

        AnalyticsEventRow lastOnPage = rows.get(1);

        assertEquals(lastOnPage.eventTime(), cursor.eventTime(),
                "The cursor must name the last row returned, not the look-ahead row");
        assertEquals(lastOnPage.eventId(), cursor.eventId());
    }

    @Test
    void shouldPassTheDecodedCursorToClickHouse() {

        stubEvents(rows(1));

        AnalyticsCursor position = new AnalyticsCursor(
                Instant.parse("2026-10-05T09:31:22.104Z"),
                UUID.randomUUID());

        service.getUserEvents(USER_ID, null, null, null, position.encode(), 2);

        ArgumentCaptor<AnalyticsCursor> captor = ArgumentCaptor.forClass(AnalyticsCursor.class);

        verify(clickHouseService).findUserEvents(
                eq(USER_ID), isNull(), isNull(), isNull(), captor.capture(), anyInt());

        assertEquals(position, captor.getValue());
    }

    @Test
    void shouldTreatABlankCursorAsTheFirstPage() {

        stubEvents(rows(1));

        service.getUserEvents(USER_ID, null, null, null, "   ", 2);

        verify(clickHouseService).findUserEvents(
                eq(USER_ID), isNull(), isNull(), isNull(), isNull(), anyInt());
    }

    @Test
    void shouldRejectAnUnparseableCursor() {

        assertThrows(
                BadRequestException.class,
                () -> service.getUserEvents(USER_ID, null, null, null, "garbage", 20));
    }

    @Test
    void shouldFallBackToTheDefaultPageSize() {

        stubEvents(rows(1));

        service.getUserEvents(USER_ID, null, null, null, null, 0);

        verify(clickHouseService).findUserEvents(
                eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                eq(AdminAnalyticsQueryService.DEFAULT_PAGE_SIZE + 1));
    }

    @Test
    void shouldRejectAPageSizeBeyondTheMaximum() {

        // Rejected rather than clamped: silently returning fewer rows than were
        // asked for would page on a size the server never honoured.
        assertThrows(
                BadRequestException.class,
                () -> service.getUserEvents(
                        USER_ID, null, null, null, null,
                        AdminAnalyticsQueryService.MAX_PAGE_SIZE + 1));
    }

    @Test
    void shouldAcceptAPageSizeAtTheMaximum() {

        stubEvents(rows(1));

        service.getUserEvents(USER_ID, null, null, null, null,
                AdminAnalyticsQueryService.MAX_PAGE_SIZE);

        verify(clickHouseService).findUserEvents(
                eq(USER_ID), isNull(), isNull(), isNull(), isNull(),
                eq(AdminAnalyticsQueryService.MAX_PAGE_SIZE + 1));
    }

    @Test
    void shouldRejectAFromTimeAfterTheToTime() {

        assertThrows(
                BadRequestException.class,
                () -> service.getUserEvents(
                        USER_ID,
                        null,
                        Instant.parse("2026-10-06T00:00:00Z"),
                        Instant.parse("2026-10-01T00:00:00Z"),
                        null,
                        20));
    }

    @Test
    void shouldPassTheFiltersThroughToClickHouse() {

        stubEvents(rows(1));

        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-06T00:00:00Z");

        service.getUserEvents(USER_ID, AnalyticsEventType.BOND_VIEW, from, to, null, 20);

        verify(clickHouseService).findUserEvents(
                eq(USER_ID), eq(AnalyticsEventType.BOND_VIEW), eq(from), eq(to),
                isNull(), anyInt());
    }

    @Test
    void shouldTotalTheSummaryAcrossEventTypes() {

        stubEvents(rows(1));

        Map<String, Long> byType = new LinkedHashMap<>();
        byType.put("BOND_VIEW", 30L);
        byType.put("LOGIN", 12L);

        when(clickHouseService.summariseUserEvents(any(), any(), any(), any()))
                .thenReturn(byType);

        AnalyticsUserEventsResponse response = service.getUserEvents(
                USER_ID, null, null, null, null, 20);

        assertEquals(42L, response.summary().totalEvents());
        assertEquals(byType, response.summary().eventsByType());
    }

    @Test
    void shouldReportAnEmptySummaryForAUserWithNoEvents() {

        stubEvents(List.of());

        when(clickHouseService.summariseUserEvents(any(), any(), any(), any()))
                .thenReturn(Map.of());

        AnalyticsUserEventsResponse response = service.getUserEvents(
                USER_ID, null, null, null, null, 20);

        assertEquals(0L, response.summary().totalEvents());
        assertTrue(response.summary().eventsByType().isEmpty());
        assertTrue(response.items().isEmpty());
        assertFalse(response.hasNext());
        assertNull(response.nextCursor());
    }

    @Test
    void shouldMapARowOntoTheResponseShape() {

        AnalyticsEventRow row = row(0);
        stubEvents(List.of(row));

        AnalyticsUserEventsResponse response = service.getUserEvents(
                USER_ID, null, null, null, null, 20);

        var item = response.items().get(0);

        assertEquals(row.eventId(), item.eventId());
        assertEquals(row.eventType(), item.eventType());
        assertEquals(row.sessionId(), item.sessionId());
        assertEquals(row.bondId(), item.bondId());
        assertEquals(row.eventTime(), item.eventTime());
        assertEquals(row.source(), item.source());
        assertEquals(row.page(), item.page());
        assertEquals(row.metadata(), item.metadata());
    }

    @Test
    void shouldEchoTheUserIdItWasAskedAbout() {

        stubEvents(List.of());

        AnalyticsUserEventsResponse response = service.getUserEvents(
                USER_ID, null, null, null, null, 20);

        assertEquals(USER_ID, response.userId());
    }

    @Test
    void shouldSearchAcrossEveryUserWithoutNamingOne() {

        stubSearch(rows(1));

        service.searchEvents(AnalyticsEventType.BOND_VIEW, null, null, null, 20);

        // No user id reaches ClickHouse: the filter is the event name, not a
        // person, and the whole point of the endpoint is that it is not scoped.
        verify(clickHouseService).findAllEvents(
                eq(AnalyticsEventType.BOND_VIEW), isNull(), isNull(), isNull(), eq(21));
    }

    @Test
    void shouldAskClickHouseForOneRowMoreThanTheSearchPageSize() {

        stubSearch(rows(3));

        service.searchEvents(null, null, null, null, 2);

        verify(clickHouseService).findAllEvents(isNull(), isNull(), isNull(), isNull(), eq(3));
    }

    @Test
    void shouldNotLeakTheSearchLookAheadRowToTheCaller() {

        stubSearch(rows(3));

        AnalyticsEventSearchResponse response = service.searchEvents(null, null, null, null, 2);

        assertEquals(2, response.items().size());
        assertEquals(2, response.size());
        assertTrue(response.hasNext());
        assertNotNull(response.nextCursor());
    }

    @Test
    void shouldPointTheSearchCursorAtTheLastRowOfThePage() {

        List<AnalyticsEventRow> rows = rows(3);
        stubSearch(rows);

        AnalyticsEventSearchResponse response = service.searchEvents(null, null, null, null, 2);

        AnalyticsCursor cursor = AnalyticsCursor.decode(response.nextCursor());

        assertEquals(rows.get(1).eventTime(), cursor.eventTime());
        assertEquals(rows.get(1).eventId(), cursor.eventId());
    }

    @Test
    void shouldReportTheLastSearchPageWhenNoLookAheadRowArrived() {

        stubSearch(rows(2));

        AnalyticsEventSearchResponse response = service.searchEvents(null, null, null, null, 2);

        assertFalse(response.hasNext());
        assertNull(response.nextCursor());
    }

    @Test
    void shouldPassTheSearchCursorAndFiltersThroughToClickHouse() {

        stubSearch(rows(1));

        AnalyticsCursor position = new AnalyticsCursor(
                Instant.parse("2026-10-05T09:31:22.104Z"),
                UUID.randomUUID());

        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-06T00:00:00Z");

        service.searchEvents(AnalyticsEventType.LOGIN, from, to, position.encode(), 20);

        verify(clickHouseService).findAllEvents(
                eq(AnalyticsEventType.LOGIN), eq(from), eq(to), eq(position), eq(21));
    }

    @Test
    void shouldCarryTheUserIdOnEachSearchedEvent() {

        AnalyticsEventRow row = row(0);
        stubSearch(List.of(row));

        AnalyticsEventSearchResponse response = service.searchEvents(null, null, null, null, 20);

        var item = response.items().get(0);

        // The per-user envelope carries the id; a search has no such envelope,
        // so each row must.
        assertEquals(row.userId(), item.userId());
        assertEquals(row.eventId(), item.eventId());
        assertEquals(row.eventType(), item.eventType());
        assertEquals(row.sessionId(), item.sessionId());
        assertEquals(row.bondId(), item.bondId());
        assertEquals(row.eventTime(), item.eventTime());
        assertEquals(row.source(), item.source());
        assertEquals(row.page(), item.page());
        assertEquals(row.metadata(), item.metadata());
    }

    @Test
    void shouldReportAnEmptySearchRatherThanFailWhenNothingMatches() {

        stubSearch(List.of());

        AnalyticsEventSearchResponse response = service.searchEvents(null, null, null, null, 20);

        assertTrue(response.items().isEmpty());
        assertEquals(0, response.size());
        assertFalse(response.hasNext());
        assertNull(response.nextCursor());
    }

    @Test
    void shouldApplyTheSamePageSizeBoundsToASearch() {

        assertThrows(
                BadRequestException.class,
                () -> service.searchEvents(
                        null, null, null, null,
                        AdminAnalyticsQueryService.MAX_PAGE_SIZE + 1));

        stubSearch(rows(1));

        service.searchEvents(null, null, null, null, 0);

        verify(clickHouseService).findAllEvents(
                isNull(), isNull(), isNull(), isNull(),
                eq(AdminAnalyticsQueryService.DEFAULT_PAGE_SIZE + 1));
    }

    @Test
    void shouldRejectASearchWithTheSameRangeAndCursorFaults() {

        assertThrows(
                BadRequestException.class,
                () -> service.searchEvents(
                        null,
                        Instant.parse("2026-10-06T00:00:00Z"),
                        Instant.parse("2026-10-01T00:00:00Z"),
                        null,
                        20));

        assertThrows(
                BadRequestException.class,
                () -> service.searchEvents(null, null, null, "garbage", 20));
    }

    /** Stubs the cross-user read, so only the search paging is under test. */
    private void stubSearch(List<AnalyticsEventRow> rows) {

        when(clickHouseService.findAllEvents(any(), any(), any(), any(), anyInt()))
                .thenReturn(rows);
    }

    /** Stubs both ClickHouse reads, so only the paging is under test. */
    private void stubEvents(List<AnalyticsEventRow> rows) {

        when(clickHouseService.findUserEvents(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(rows);

        org.mockito.Mockito.lenient()
                .when(clickHouseService.summariseUserEvents(any(), any(), any(), any()))
                .thenReturn(Map.of());
    }

    /** @return {@code count} rows, newest first */
    private static List<AnalyticsEventRow> rows(int count) {

        List<AnalyticsEventRow> rows = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            rows.add(row(i));
        }

        return rows;
    }

    private static AnalyticsEventRow row(int index) {

        return new AnalyticsEventRow(
                UUID.randomUUID(),
                "BOND_VIEW",
                USER_ID,
                "session-" + index,
                UUID.randomUUID(),
                Instant.parse("2026-10-06T09:00:00Z").minusSeconds(index),
                "WEB",
                "BOND_DETAILS",
                Map.of("index", index));
    }
}
