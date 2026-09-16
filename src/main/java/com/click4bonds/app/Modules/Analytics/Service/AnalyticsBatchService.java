package com.click4bonds.app.Modules.Analytics.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Collects consumed analytics events and hands them to ClickHouse in batches.
 *
 * <p>Batching exists because ClickHouse is far better at one insert of a
 * thousand rows than at a thousand inserts of one. Events are held in memory
 * until either {@value #BATCH_SIZE} of them have accumulated or the scheduler
 * fires, whichever happens first — so busy periods flush on size and quiet ones
 * flush on the timer.</p>
 *
 * <p>The buffer is guarded by a {@link ReentrantLock} rather than made a
 * {@code synchronized} method. The lock is released before the insert: holding
 * it across a ClickHouse round trip would block the Kafka listener thread, and
 * with it every other partition assignment on the container, for as long as
 * ClickHouse takes to answer.</p>
 *
 * <p>Flushing is safe to run concurrently. Each flush takes a disjoint snapshot
 * under the lock, so no event is ever inserted twice; two overlapping flushes
 * simply split the backlog between them.</p>
 */
@Service
@Slf4j
public class AnalyticsBatchService {

    /** Events buffered before a flush is triggered from {@link #add}. */
    public static final int BATCH_SIZE = 1000;

    /**
     * Hard ceiling on buffered events. Reached only when ClickHouse is
     * unreachable and traffic keeps arriving, in which case the oldest events
     * are dropped — see {@link #add}.
     */
    public static final int MAX_BUFFER_SIZE = 10_000;

    /** How often a partial batch is flushed when traffic is too low to fill one. */
    public static final long FLUSH_INTERVAL_MILLIS = 5000;

    private final ClickHouseService clickHouseService;

    /** Oldest first; consumed by {@link #drain()}. */
    private final Deque<AnalyticsEvent> buffer = new ArrayDeque<>();

    private final ReentrantLock lock = new ReentrantLock();

    private final AtomicLong droppedEvents = new AtomicLong();

    /**
     * {@code System.nanoTime()} of the last failed flush, or {@code 0} when the
     * last flush succeeded. Used only to stop {@link #add} from retrying while
     * ClickHouse is known to be failing — see {@link #inFailureBackoff()}.
     */
    private final AtomicLong lastFlushFailureNanos = new AtomicLong();

    /**
     * {@code System.nanoTime()} of the last overflow warning. While the buffer
     * is over capacity every incoming event drops one more, so warning per drop
     * would write a log line per event for as long as the outage lasts.
     */
    private final AtomicLong lastOverflowWarningNanos = new AtomicLong();

    public AnalyticsBatchService(ClickHouseService clickHouseService) {
        this.clickHouseService = clickHouseService;
    }

    /**
     * Buffers one event, flushing immediately once the batch size is reached.
     *
     * <p>When the buffer is already at {@link #MAX_BUFFER_SIZE} the oldest
     * event is dropped to make room. That only happens while ClickHouse is
     * failing, and dropping the oldest loses less than dropping the incoming
     * event would: the newest events are the ones an operator is watching for.
     * The count is kept in {@link #droppedEventCount()} and logged.</p>
     *
     * @param event the event to buffer; {@code null} is ignored
     */
    public void add(AnalyticsEvent event) {

        if (event == null) {
            return;
        }

        lock.lock();

        try {
            buffer.addLast(event);
            trimLocked();
        } finally {
            lock.unlock();
        }

        if (size() >= BATCH_SIZE && !inFailureBackoff()) {
            flush();
        }
    }

    /**
     * Whether a flush failed recently enough that {@link #add} should not start
     * another one.
     *
     * <p>Without this, a ClickHouse outage turns every incoming event into a
     * failed flush of the whole buffer: the events are drained, the insert
     * fails, they are restored, and the next event repeats it. That is a hot
     * loop against a service that is already struggling, and it is least
     * welcome exactly then.</p>
     *
     * <p>While the window is open only {@link #scheduledFlush()} retries, which
     * puts the retry rate at one attempt per interval regardless of traffic.
     * The window closes as soon as a flush succeeds.</p>
     *
     * @return {@code true} if a recent flush failed
     */
    private boolean inFailureBackoff() {

        long lastFailure = lastFlushFailureNanos.get();

        return lastFailure != 0
                && (System.nanoTime() - lastFailure) < Duration.ofMillis(FLUSH_INTERVAL_MILLIS).toNanos();
    }

    /**
     * Flushes whatever is buffered, in the background.
     *
     * <p>Runs every {@value #FLUSH_INTERVAL_MILLIS} milliseconds after the
     * previous run finished. Does nothing when the buffer is empty.</p>
     */
    @Scheduled(fixedDelay = FLUSH_INTERVAL_MILLIS)
    public void scheduledFlush() {
        flush();
    }

    /**
     * Sends the currently buffered events to ClickHouse.
     *
     * <p>Events are removed from the buffer before the insert rather than after
     * it, so a slow insert cannot be overtaken by a second flush that would
     * send the same events again. If the insert then fails they are put back at
     * the front of the buffer, in their original order, for the next flush to
     * retry.</p>
     *
     * @return how many events were written; {@code 0} when the buffer was empty
     *         or the insert failed
     */
    public int flush() {

        List<AnalyticsEvent> batch = drain();

        if (batch.isEmpty()) {
            return 0;
        }

        try {
            clickHouseService.insertBatch(batch);

            lastFlushFailureNanos.set(0);

            log.debug("Flushed {} analytics events to ClickHouse", batch.size());

            return batch.size();

        } catch (RuntimeException e) {

            lastFlushFailureNanos.set(System.nanoTime());

            restore(batch);

            log.error(
                    "Failed to flush {} analytics events to ClickHouse; "
                            + "returned them to the buffer to retry on the next flush",
                    batch.size(),
                    e);

            return 0;
        }
    }

    /**
     * Writes anything still buffered before the application stops.
     *
     * <p>Buffered events exist only in memory, so without this a restart would
     * silently lose up to {@value #BATCH_SIZE} events. A failure here is logged
     * and swallowed: the context is shutting down either way, and throwing
     * would turn a shutdown into a crash.</p>
     */
    @PreDestroy
    public void flushOnShutdown() {

        int buffered = size();

        if (buffered == 0) {
            return;
        }

        log.info("Flushing {} buffered analytics events before shutdown", buffered);

        flush();
    }

    /**
     * @return number of events currently waiting to be written
     */
    public int size() {

        lock.lock();

        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return events dropped because the buffer was full, since startup
     */
    public long droppedEventCount() {
        return droppedEvents.get();
    }

    /**
     * Takes everything currently buffered, leaving the buffer empty.
     *
     * @return the buffered events in arrival order, empty when there were none
     */
    private List<AnalyticsEvent> drain() {

        lock.lock();

        try {
            if (buffer.isEmpty()) {
                return List.of();
            }

            List<AnalyticsEvent> batch = new ArrayList<>(buffer);

            buffer.clear();

            return batch;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Puts a failed batch back at the front of the buffer, oldest first, so it
     * is retried before anything that arrived after it.
     *
     * @param batch the events to restore
     */
    private void restore(List<AnalyticsEvent> batch) {

        lock.lock();

        try {
            for (int i = batch.size() - 1; i >= 0; i--) {
                buffer.addFirst(batch.get(i));
            }

            trimLocked();

        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops oldest events until the buffer is back within
     * {@link #MAX_BUFFER_SIZE}. Call only with the lock held.
     */
    private void trimLocked() {

        if (buffer.size() <= MAX_BUFFER_SIZE) {
            return;
        }

        long dropped = 0;

        while (buffer.size() > MAX_BUFFER_SIZE) {
            buffer.pollFirst();
            dropped++;
        }

        long totalDropped = droppedEvents.addAndGet(dropped);

        warnAboutOverflowAtMostOncePerInterval(dropped, totalDropped);
    }

    /**
     * Reports an overflow, but not more than once per flush interval — during
     * an outage every event overflows, and one line per event would bury the
     * rest of the log.
     *
     * @param dropped      events dropped by this call
     * @param totalDropped events dropped since startup
     */
    private void warnAboutOverflowAtMostOncePerInterval(long dropped, long totalDropped) {

        long now = System.nanoTime();
        long lastWarning = lastOverflowWarningNanos.get();

        long intervalNanos = Duration.ofMillis(FLUSH_INTERVAL_MILLIS).toNanos();

        if (lastWarning != 0 && (now - lastWarning) < intervalNanos) {
            return;
        }

        if (lastOverflowWarningNanos.compareAndSet(lastWarning, now)) {

            log.warn(
                    "Analytics buffer is full at {} events; dropped the {} oldest. "
                            + "ClickHouse is probably unreachable. {} dropped in total.",
                    MAX_BUFFER_SIZE, dropped, totalDropped);
        }
    }
}
