package com.click4bonds.app.Modules.Analytics.Controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Development-only endpoint that pushes one analytics event through the whole
 * pipeline, to prove the wiring end to end.
 *
 * <p>Bound to the {@code dev} profile. A production deployment does not
 * activate that profile, so this controller is never registered and the path
 * simply does not exist there — the guard is a missing bean rather than a
 * runtime check that could be forgotten.</p>
 *
 * <p>It is also the reason {@code /api/analytics/test} would have to be
 * permitted in {@code SecurityConfig}: a smoke test is only useful if it can be
 * called without signing in first. That rule is currently commented out along
 * with the rest of this path's configuration.</p>
 *
 * <p>What the caller should see afterwards:</p>
 *
 * <pre>
 * POST /api/analytics/test
 *   -&gt; AnalyticsService.track
 *   -&gt; AnalyticsEventProducer
 *   -&gt; Kafka topic click4bonds.analytics
 *   -&gt; AnalyticsEventConsumer
 *   -&gt; AnalyticsBatchService (buffered, then flushed)
 *   -&gt; ClickHouseService
 *   -&gt; click4bonds_analytics.analytics_events
 * </pre>
 *
 * <p>A {@code 202} only means the event reached the producer. The row appears
 * in ClickHouse after the next flush — at most
 * {@code AnalyticsBatchService.FLUSH_INTERVAL_MILLIS} away.</p>
 */
@RestController
@RequestMapping("/api/analytics/test")
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsTestController {

    /** Marks test traffic so it can be filtered out of real analytics. */
    public static final String TEST_SOURCE = "TEST";

    private final AnalyticsService analyticsService;

    /**
     * Publishes one synthetic {@code BOND_VIEW}.
     *
     * @param userId    optional user id to attribute the event to
     * @param sessionId optional session id; defaults to a recognisable marker
     * @param bondId    optional bond id
     * @return {@code 202 Accepted} with the event that was emitted
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> publishTestEvent(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) UUID bondId) {

        String testSessionId = (sessionId != null) ? sessionId : "analytics-smoke-test";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("action", "smoke-test");
        metadata.put("origin", "AnalyticsTestController");

        analyticsService.track(
                AnalyticsEventType.BOND_VIEW,
                userId,
                testSessionId,
                bondId,
                TEST_SOURCE,
                "ANALYTICS_TEST",
                metadata);

        log.info("Published an analytics smoke-test event (userId={}, bondId={})", userId, bondId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "published");
        body.put("eventType", AnalyticsEventType.BOND_VIEW.name());
        body.put("source", TEST_SOURCE);
        body.put("page", "ANALYTICS_TEST");
        body.put("sessionId", testSessionId);
        body.put("note", "Accepted by the producer. The row reaches ClickHouse on the next batch flush.");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }
}
