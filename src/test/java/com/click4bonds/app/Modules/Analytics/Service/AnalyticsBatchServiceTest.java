package com.click4bonds.app.Modules.Analytics.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.click4bonds.app.Modules.Analytics.Exception.AnalyticsStorageException;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;

/**
 * How {@link AnalyticsBatchService} buffers, flushes, recovers and acknowledges.
 *
 * <p>{@link ClickHouseService} is mocked, so nothing here waits on a server or
 * on the real five second scheduler — flushes are called directly. The
 * {@link Acknowledgment} is mocked too, which is what makes it possible to
 * assert the ordering that keeps events from being lost: an offset may only be
 * committed once the batch it belongs to is in ClickHouse.</p>
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsBatchServiceTest {

    @Mock
    private ClickHouseService clickHouseService;

    @Mock
    private Acknowledgment acknowledgment;

    private AnalyticsBatchService batchService;

    @BeforeEach
    void setUp() {
        batchService = new AnalyticsBatchService(clickHouseService);
    }

    @Test
    void shouldBufferAnEventWithoutTouchingClickHouse() {

        add(event());

        assertEquals(1, batchService.size());
        verifyNoInteractions(clickHouseService);
    }

    @Test
    void shouldFlushOnceTheBatchSizeIsReached() {

        for (int i = 0; i < AnalyticsBatchService.BATCH_SIZE; i++) {
            add(event());
        }

        List<List<AnalyticsEvent>> batches = batchesSentToClickHouse();

        assertEquals(1, batches.size(), "The thousandth event should have triggered exactly one flush");
        assertEquals(AnalyticsBatchService.BATCH_SIZE, batches.get(0).size());
        assertEquals(0, batchService.size(), "A completed flush must leave the buffer empty");
    }

    @Test
    void shouldKeepBufferingBelowTheBatchSize() {

        for (int i = 0; i < AnalyticsBatchService.BATCH_SIZE - 1; i++) {
            add(event());
        }

        assertEquals(AnalyticsBatchService.BATCH_SIZE - 1, batchService.size());
        verify(clickHouseService, never()).insertBatch(anyList());
    }

    @Test
    void shouldFlushAPartialBatchOnTheTimer() {

        add(event());
        add(event());
        add(event());

        batchService.scheduledFlush();

        List<List<AnalyticsEvent>> batches = batchesSentToClickHouse();

        assertEquals(1, batches.size());
        assertEquals(3, batches.get(0).size());
        assertEquals(0, batchService.size());
    }

    @Test
    void shouldDoNothingWhenTheBufferIsEmpty() {

        batchService.scheduledFlush();

        assertEquals(0, batchService.flush());
        verifyNoInteractions(clickHouseService);
    }

    @Test
    void shouldHandClickHouseTheEventsInArrivalOrder() {

        AnalyticsEvent first = event();
        AnalyticsEvent second = event();
        AnalyticsEvent third = event();

        add(first);
        add(second);
        add(third);

        batchService.flush();

        assertEquals(List.of(first, second, third), batchesSentToClickHouse().get(0));
    }

    @Test
    void shouldReturnEventsToTheBufferWhenTheInsertFails() {

        doThrow(new AnalyticsStorageException("ClickHouse is down", null))
                .when(clickHouseService).insertBatch(anyList());

        AnalyticsEvent first = event();
        AnalyticsEvent second = event();
        AnalyticsEvent third = event();

        add(first);
        add(second);
        add(third);

        assertEquals(0, batchService.flush(), "A failed flush writes nothing");
        assertEquals(3, batchService.size(), "The events must come back for another attempt");

        doNothing().when(clickHouseService).insertBatch(anyList());

        assertEquals(3, batchService.flush(), "The retry should write all three");

        List<List<AnalyticsEvent>> batches = batchesSentToClickHouse();

        assertEquals(2, batches.size(), "One failed attempt and one retry");
        assertEquals(List.of(first, second, third), batches.get(1),
                "Retrying must preserve the original order");
    }

    @Test
    void shouldAcknowledgeEveryEventOnceTheInsertSucceeds() {

        add(event());
        add(event());
        add(event());

        assertEquals(3, batchService.flush());

        verify(acknowledgment, times(3)).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeAnythingWhenTheInsertFails() {

        doThrow(new AnalyticsStorageException("ClickHouse is down", null))
                .when(clickHouseService).insertBatch(anyList());

        add(event());
        add(event());

        assertEquals(0, batchService.flush());

        // Nothing reached ClickHouse, so Kafka must still own these records: the
        // offset is the only thing that can bring them back.
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shouldKeepAnEventUnacknowledgedUntilAFlushFinallySucceeds() {

        doThrow(new AnalyticsStorageException("ClickHouse is down", null))
                .when(clickHouseService).insertBatch(anyList());

        AnalyticsEvent event = event();

        add(event);

        assertEquals(0, batchService.flush());

        assertEquals(1, batchService.size(), "The event must survive the failed flush");
        verify(acknowledgment, never()).acknowledge();

        doNothing().when(clickHouseService).insertBatch(anyList());

        assertEquals(1, batchService.flush(), "The retry should write the event");

        assertEquals(0, batchService.size());
        verify(acknowledgment).acknowledge();

        assertEquals(List.of(event), batchesSentToClickHouse().get(1),
                "The retry must carry the same event");
    }

    @Test
    void shouldAcknowledgeOnlyAfterClickHouseHasAcceptedTheBatch() {

        add(event());

        batchService.flush();

        InOrder order = inOrder(clickHouseService, acknowledgment);

        order.verify(clickHouseService).insertBatch(anyList());
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldNotTurnEveryIncomingEventIntoAFailedInsertWhileClickHouseIsDown() {

        doThrow(new AnalyticsStorageException("ClickHouse is down", null))
                .when(clickHouseService).insertBatch(anyList());

        // Fill the buffer once so the size-triggered flush happens and fails.
        for (int i = 0; i < AnalyticsBatchService.BATCH_SIZE; i++) {
            add(event());
        }

        int attemptsAfterFirstFailure = batchesSentToClickHouse().size();

        assertEquals(1, attemptsAfterFirstFailure);

        // Keep the traffic coming. Without a back-off every one of these adds
        // would drain the buffer and fail again.
        for (int i = 0; i < 5 * AnalyticsBatchService.BATCH_SIZE; i++) {
            add(event());
        }

        // Without the back-off each of those 5000 adds would have drained the
        // buffer and failed again, so anything near 5000 would fail this.
        assertTrue(batchesSentToClickHouse().size() <= attemptsAfterFirstFailure + 1,
                "A known-failing ClickHouse must only be retried on the timer, not per event");
    }

    @Test
    void shouldDropTheOldestEventsWhenTheBufferOverflows() {

        doThrow(new AnalyticsStorageException("ClickHouse is down", null))
                .when(clickHouseService).insertBatch(anyList());

        int overflow = 500;

        for (int i = 0; i < AnalyticsBatchService.MAX_BUFFER_SIZE + overflow; i++) {
            add(event());
        }

        assertEquals(AnalyticsBatchService.MAX_BUFFER_SIZE, batchService.size(),
                "The buffer must stay bounded when ClickHouse cannot keep up");
        assertEquals(overflow, batchService.droppedEventCount());
    }

    @Test
    void shouldNotCorruptTheBufferUnderConcurrentAdds() throws Exception {

        int threads = 8;
        int perThread = 100;
        int total = threads * perThread;

        // Well below BATCH_SIZE so no automatic flush interferes with the count.
        assertTrue(total < AnalyticsBatchService.BATCH_SIZE);

        List<AnalyticsEvent> events = IntStream.range(0, total)
                .mapToObj(i -> event())
                .toList();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int t = 0; t < threads; t++) {
                int from = t * perThread;

                pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    for (int i = from; i < from + perThread; i++) {
                        add(events.get(i));
                    }
                });
            }

            start.countDown();
            pool.shutdown();

            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "Adds did not finish");

        } finally {
            pool.shutdownNow();
        }

        assertEquals(total, batchService.size(), "Concurrent adds must lose nothing");

        batchService.flush();

        List<AnalyticsEvent> flushed = batchesSentToClickHouse().get(0);

        assertEquals(total, flushed.size());

        Set<UUID> flushedIds = ConcurrentHashMap.newKeySet();
        flushed.forEach(e -> flushedIds.add(e.eventId()));

        assertEquals(total, flushedIds.size(), "No event may be buffered twice");
        assertEquals(
                Set.copyOf(events.stream().map(AnalyticsEvent::eventId).toList()),
                flushedIds,
                "Every event added must be the one flushed");
    }

    @Test
    void shouldWriteWhatIsBufferedOnShutdown() {

        add(event());
        add(event());

        batchService.flushOnShutdown();

        assertEquals(2, batchesSentToClickHouse().get(0).size());
    }

    @Test
    void shouldAcknowledgeWhatItWritesOnShutdown() {

        add(event());
        add(event());

        batchService.flushOnShutdown();

        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    void shouldNotReachClickHouseOnShutdownWithAnEmptyBuffer() {

        batchService.flushOnShutdown();

        verifyNoInteractions(clickHouseService);
    }

    /**
     * Buffers one event against the shared acknowledgment mock.
     *
     * <p>Every test here is about what happens to a buffered event, not about
     * which record it came from, so one acknowledgment stands in for all of
     * them and the assertions count how often it is used.</p>
     *
     * @param event the event to buffer
     */
    private void add(AnalyticsEvent event) {
        batchService.add(event, acknowledgment);
    }

    /**
     * @return every batch handed to the mocked {@link ClickHouseService}, oldest
     *         call first; empty when it was never called
     */
    private List<List<AnalyticsEvent>> batchesSentToClickHouse() {

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnalyticsEvent>> captor = ArgumentCaptor.forClass(List.class);

        verify(clickHouseService, atLeastOnce()).insertBatch(captor.capture());

        return captor.getAllValues();
    }

    private static AnalyticsEvent event() {

        return new AnalyticsEvent(
                UUID.randomUUID(),
                AnalyticsEventType.BOND_VIEW,
                UUID.randomUUID(),
                "session-1",
                UUID.randomUUID(),
                Instant.now(),
                "WEB",
                "BOND_DETAILS",
                Map.of());
    }
}
