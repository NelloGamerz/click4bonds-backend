package com.click4bonds.app.Modules.Analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole analytics pipeline, from an HTTP request to a row in ClickHouse.
 *
 * <p>Named {@code *IT} rather than {@code *Test} on purpose. Maven Surefire only
 * picks up {@code *Test} classes, so {@code mvn test} skips this; it needs the
 * local stack from {@code click4bonds-infra} (Kafka on 9092, ClickHouse on 8123)
 * plus the PostgreSQL and Redis the application context always needs.</p>
 *
 * <p>Run it with:</p>
 *
 * <pre>
 * ./mvnw test -Dtest=AnalyticsPipelineIT
 * </pre>
 *
 * <p>It asserts the flow described in {@code docs/analytics.md}:</p>
 *
 * <pre>
 * POST /api/analytics/test
 *   -&gt; AnalyticsService -&gt; Kafka -&gt; AnalyticsEventConsumer
 *   -&gt; AnalyticsBatchService -&gt; ClickHouseService -&gt; analytics_events
 * </pre>
 *
 * <p>The row is allowed to arrive via the scheduled flush rather than an
 * explicit one, so this covers the timer as well. The test therefore waits.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AnalyticsPipelineIT {

    /** The scheduler flushes every five seconds; allow several rounds. */
    private static final Duration ARRIVAL_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Value("${clickhouse.url}")
    private String clickHouseUrl;

    @LocalServerPort
    private int port;

    @Test
    void shouldCarryAnEventFromTheHttpEndpointIntoClickHouse() throws Exception {

        assumeTrue(clickHouseIsReachable(),
                "ClickHouse is not reachable at " + httpBaseUrl()
                        + " - start it with 'docker compose up -d' in click4bonds-infra");

        // Unique per run, so repeated runs cannot see each other's rows and the
        // assertions below always describe exactly one event.
        String sessionId = "it-" + UUID.randomUUID();

        HttpResponse<String> response = publishTestEvent(sessionId, 4242L, 99L);

        assertEquals(202, response.statusCode(),
                "The endpoint should accept the event: " + response.body());

        String row = awaitRowFor(sessionId);

        assertFalse(row.isEmpty(),
                "No row reached ClickHouse within " + ARRIVAL_TIMEOUT
                        + " - check that Kafka and ClickHouse are up and that the consumer is running");

        String[] columns = row.split("\t", -1);

        assertEquals("BOND_VIEW", columns[0], "event_type");
        assertEquals("4242", columns[1], "user_id");
        assertEquals(sessionId, columns[2], "session_id");
        assertEquals("99", columns[3], "bond_id");
        assertEquals("TEST", columns[4], "source");
        assertEquals("ANALYTICS_TEST", columns[5], "page");

        Map<String, Object> metadata = objectMapper.readValue(
                columns[6], new TypeReference<Map<String, Object>>() {
                });

        assertEquals("smoke-test", metadata.get("action"));
        assertEquals("AnalyticsTestController", metadata.get("origin"));

        assertTrue(timestampIsPlausible(sessionId),
                "event_time did not land within minutes of now(), which usually means a timezone "
                        + "mismatch between the JDBC driver and the ClickHouse server");
    }

    @Test
    void shouldStoreAnAnonymousEventWithoutUserOrBond() throws Exception {

        assumeTrue(clickHouseIsReachable(), "ClickHouse is not reachable");

        String sessionId = "it-anon-" + UUID.randomUUID();

        // No userId and no bondId: the endpoint passes them through as null, and
        // they have to reach Nullable(UInt64) columns. Binding a null into a
        // non-nullable column instead would fail the whole batch.
        publishTestEvent(sessionId, null, null);

        String row = awaitRowFor(sessionId);

        assertFalse(row.isEmpty(), "The anonymous event never reached ClickHouse");

        String[] columns = row.split("\t", -1);

        assertEquals("\\N", columns[1], "user_id should be stored as NULL");
        assertEquals("\\N", columns[3], "bond_id should be stored as NULL");
        assertEquals(sessionId, columns[2], "session_id");
    }

    @Test
    void shouldNotWriteTheSameEventTwice() throws Exception {

        assumeTrue(clickHouseIsReachable(), "ClickHouse is not reachable");

        String sessionId = "it-dup-" + UUID.randomUUID();

        publishTestEvent(sessionId, 1L, 1L);

        awaitRowFor(sessionId);

        // Give the scheduler a couple more rounds: a duplicate would be written
        // by a second flush, not by the first one.
        Thread.sleep(Duration.ofSeconds(6).toMillis());

        assertEquals(1, countRowsFor(sessionId),
                "The event was stored more than once");
    }

    /**
     * Calls the development smoke-test endpoint on the running server.
     *
     * @param sessionId marker that identifies this run's event
     * @param userId    user to attribute the event to, omitted when null so the
     *                  endpoint sees a genuinely absent parameter
     * @param bondId    bond to attribute the event to, omitted when null
     * @return the server's response
     */
    private HttpResponse<String> publishTestEvent(String sessionId, Long userId, Long bondId)
            throws Exception {

        StringBuilder url = new StringBuilder("http://localhost:")
                .append(port)
                .append("/api/analytics/test?sessionId=")
                .append(sessionId);

        if (userId != null) {
            url.append("&userId=").append(userId);
        }

        if (bondId != null) {
            url.append("&bondId=").append(bondId);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Polls ClickHouse until the event shows up.
     *
     * @param sessionId the unique marker identifying the event
     * @return the TSV row, or an empty string if it never arrived
     */
    private String awaitRowFor(String sessionId) throws Exception {

        long deadline = System.nanoTime() + ARRIVAL_TIMEOUT.toNanos();
        String row = "";

        while (System.nanoTime() < deadline) {

            row = query("""
                    SELECT event_type, user_id, session_id, bond_id, source, page, metadata
                    FROM click4bonds_analytics.analytics_events
                    WHERE session_id = '%s'
                    ORDER BY event_time DESC
                    LIMIT 1
                    FORMAT TSV
                    """.formatted(sessionId)).trim();

            if (!row.isEmpty()) {
                return row;
            }

            Thread.sleep(POLL_INTERVAL.toMillis());
        }

        return row;
    }

    /**
     * @param sessionId the unique marker identifying the event
     * @return {@code true} if the stored {@code event_time} is close to the
     *         server's own clock
     */
    private boolean timestampIsPlausible(String sessionId) throws Exception {

        String count = query("""
                SELECT count()
                FROM click4bonds_analytics.analytics_events
                WHERE session_id = '%s'
                  AND event_time BETWEEN now() - INTERVAL 10 MINUTE AND now() + INTERVAL 1 MINUTE
                FORMAT TSV
                """.formatted(sessionId)).trim();

        return "1".equals(count);
    }

    /**
     * @param sessionId the unique marker identifying the event
     * @return how many rows ClickHouse holds for it
     */
    private long countRowsFor(String sessionId) throws Exception {

        return Long.parseLong(query("""
                SELECT count()
                FROM click4bonds_analytics.analytics_events
                WHERE session_id = '%s'
                FORMAT TSV
                """.formatted(sessionId)).trim());
    }

    /**
     * Runs one query through ClickHouse's HTTP interface.
     *
     * <p>Deliberately not the application's own JDBC datasource: going in over
     * HTTP means this test verifies the rows independently of the code that
     * wrote them.</p>
     *
     * @param sql the query, must end in {@code FORMAT TSV}
     * @return the response body
     */
    private String query(String sql) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(httpBaseUrl() + "/?query="
                        + URLEncoder.encode(sql, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "ClickHouse returned " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * @return {@code true} if ClickHouse answers a trivial query
     */
    private boolean clickHouseIsReachable() {

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(httpBaseUrl() + "/ping"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                    .statusCode() == 200;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Turns the configured JDBC URL into the HTTP endpoint to query.
     *
     * <p>{@code jdbc:clickhouse://localhost:8123/click4bonds_analytics} becomes
     * {@code http://localhost:8123}, so changing the port in configuration
     * keeps this test pointed at the same server.</p>
     *
     * @return the base URL of ClickHouse's HTTP interface
     */
    private String httpBaseUrl() {

        String withoutScheme = clickHouseUrl.replaceFirst("^jdbc:clickhouse://", "");
        String hostAndPort = withoutScheme.split("/", 2)[0];

        return "http://" + hostAndPort;
    }
}
