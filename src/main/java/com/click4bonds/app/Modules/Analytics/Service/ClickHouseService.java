package com.click4bonds.app.Modules.Analytics.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Analytics.Config.ClickHouseConfig;
import com.click4bonds.app.Modules.Analytics.Exception.AnalyticsQueryException;
import com.click4bonds.app.Modules.Analytics.Exception.AnalyticsStorageException;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventRow;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads and writes {@code click4bonds_analytics.analytics_events}.
 *
 * <p>This is the only class in the application that speaks ClickHouse SQL, in
 * both directions. Writes are called from the Kafka consumer thread via
 * {@code AnalyticsBatchService}; reads are called from
 * {@link AdminAnalyticsQueryService}, which serves the admin reporting
 * endpoint.</p>
 *
 * <p>The datasource is the analytics module's own and is reached through an
 * explicit qualifier — resolving {@code DataSource} by type here would risk
 * picking up the primary PostgreSQL datasource.</p>
 *
 * <h2>What the read queries cost</h2>
 *
 * <p>The table's sorting key is {@code (event_type, event_time)} and its
 * partition key is {@code toYYYYMM(event_time)}, so neither prunes on
 * {@code user_id}: every read below is a scan of the scanned partitions, not an
 * index seek. That is acceptable for an admin endpoint reading one user's
 * history, and it is the reason the read side pages by key rather than by
 * offset — see {@link AnalyticsCursor}. Adding {@code user_id} to the sorting
 * key is the change that would make these queries cheap.</p>
 */
@Service
@Slf4j
public class ClickHouseService {

    /**
     * One row per {@code ?}. Kept as a single {@code INSERT ... VALUES} with
     * placeholders rather than string-concatenated values: the JDBC driver
     * turns the batch into a bulk insert, and bound parameters cannot inject
     * SQL through {@code metadata} or {@code sessionId}.
     */
    static final String INSERT_SQL = """
            INSERT INTO click4bonds_analytics.analytics_events
            (
                event_id,
                event_type,
                user_id,
                session_id,
                bond_id,
                event_time,
                source,
                page,
                metadata
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** The table every read and write in this class targets. */
    static final String TABLE = "click4bonds_analytics.analytics_events";

    /**
     * The nine columns, in the order {@link #toRow} reads them. Named rather
     * than {@code SELECT *} so a column added to the table cannot silently
     * change what a caller receives.
     */
    static final String COLUMNS = """
            event_id,
            event_type,
            user_id,
            session_id,
            bond_id,
            event_time,
            source,
            page,
            metadata
            """;

    private final DataSource clickHouseDataSource;

    private final ObjectMapper objectMapper;

    /**
     * @param clickHouseDataSource the analytics datasource, selected by name
     *                             so it cannot be confused with the primary
     *                             PostgreSQL one
     * @param objectMapper         the application's mapper, used to render
     *                             {@code metadata} as JSON
     */
    public ClickHouseService(
            @Qualifier(ClickHouseConfig.CLICKHOUSE_DATASOURCE) DataSource clickHouseDataSource,
            ObjectMapper objectMapper) {

        this.clickHouseDataSource = clickHouseDataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Inserts every event in one JDBC batch.
     *
     * <p>An empty or {@code null} list is a no-op: no connection is opened and
     * no statement is executed.</p>
     *
     * <p>All events go through a single connection and statement, so a batch
     * either reaches ClickHouse together or raises as a unit. The caller owns
     * retrying.</p>
     *
     * @param events the events to insert, may be {@code null} or empty
     * @throws AnalyticsStorageException if the connection, binding or insert
     *                                   fails, wrapping the underlying cause
     */
    public void insertBatch(List<AnalyticsEvent> events) {

        if (events == null || events.isEmpty()) {
            return;
        }

        try (
                Connection connection = clickHouseDataSource.getConnection();

                PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {

            for (AnalyticsEvent event : events) {
                bind(statement, event);
                statement.addBatch();
            }

            statement.executeBatch();

        } catch (Exception e) {

            throw new AnalyticsStorageException(
                    "Failed to insert " + events.size() + " analytics events into ClickHouse",
                    e);
        }
    }

    /**
     * Reads one page of a user's events, newest first.
     *
     * <p>Keyset pagination rather than {@code LIMIT}/{@code OFFSET}. Every page
     * is a fresh scan of the same rows either way — {@code user_id} is not in
     * the sorting key — but keyset paging does not degrade as the caller walks
     * deeper, and it cannot skip or repeat a row when new events are inserted
     * while a caller is paging. That second property is the one that matters
     * here: this table is written to continuously by the Kafka consumer, so an
     * offset would drift under the reader.</p>
     *
     * <p>The ordering is {@code (event_time DESC, event_id DESC)}, matching the
     * comparison in {@link #whereClause} — the two have to agree exactly or the
     * page boundary falls in a different place than the cursor describes.</p>
     *
     * @param userId    the user whose events to read, required
     * @param eventType optional event name to narrow to, {@code null} for all
     * @param from      optional inclusive lower bound on {@code event_time}
     * @param to        optional inclusive upper bound on {@code event_time}
     * @param cursor    where the previous page stopped, {@code null} for the
     *                  first page
     * @param limit     maximum rows to return; the caller asks for one more than
     *                  it intends to show, to learn whether another page exists
     * @return the rows, newest first, at most {@code limit} of them
     * @throws AnalyticsQueryException if ClickHouse cannot be reached or the
     *                                 query fails
     */
    public List<AnalyticsEventRow> findUserEvents(
            UUID userId,
            AnalyticsEventType eventType,
            Instant from,
            Instant to,
            AnalyticsCursor cursor,
            int limit) {

        List<Object> parameters = new ArrayList<>();

        String sql = "SELECT "
                + COLUMNS
                + " FROM "
                + TABLE
                + whereClause(userId, eventType, from, to, cursor, parameters)
                + " ORDER BY event_time DESC, event_id DESC"
                + " LIMIT ?";

        parameters.add(limit);

        try (
                Connection connection = clickHouseDataSource.getConnection();

                PreparedStatement statement = connection.prepareStatement(sql)) {

            bindAll(statement, parameters);

            try (ResultSet rows = statement.executeQuery()) {

                List<AnalyticsEventRow> events = new ArrayList<>();

                while (rows.next()) {
                    events.add(toRow(rows));
                }

                return events;
            }

        } catch (Exception e) {

            throw new AnalyticsQueryException(
                    "Failed to read analytics events for user " + userId,
                    e);
        }
    }

    /**
     * Counts a user's events grouped by event name, most frequent first.
     *
     * <p>Deliberately independent of the page cursor: the summary describes the
     * whole filtered set, not the twenty rows currently on screen. A caller
     * paging through a year of activity should see the same totals on every
     * page, and recomputing them per page would make them climb as the caller
     * scrolled, which reads as a bug.</p>
     *
     * <p>The result is a {@link LinkedHashMap}, so the order ClickHouse returned
     * — descending by count — survives into the response.</p>
     *
     * @param userId    the user whose events to count, required
     * @param eventType optional event name to narrow to, {@code null} for all
     * @param from      optional inclusive lower bound on {@code event_time}
     * @param to        optional inclusive upper bound on {@code event_time}
     * @return event name to count; empty when the user has no matching events
     * @throws AnalyticsQueryException if ClickHouse cannot be reached or the
     *                                 query fails
     */
    public Map<String, Long> summariseUserEvents(
            UUID userId,
            AnalyticsEventType eventType,
            Instant from,
            Instant to) {

        List<Object> parameters = new ArrayList<>();

        String sql = "SELECT event_type, count() AS total FROM "
                + TABLE
                + whereClause(userId, eventType, from, to, null, parameters)
                + " GROUP BY event_type"
                + " ORDER BY total DESC, event_type ASC";

        try (
                Connection connection = clickHouseDataSource.getConnection();

                PreparedStatement statement = connection.prepareStatement(sql)) {

            bindAll(statement, parameters);

            try (ResultSet rows = statement.executeQuery()) {

                Map<String, Long> counts = new LinkedHashMap<>();

                while (rows.next()) {
                    counts.put(rows.getString("event_type"), rows.getLong("total"));
                }

                return counts;
            }

        } catch (Exception e) {

            throw new AnalyticsQueryException(
                    "Failed to summarise analytics events for user " + userId,
                    e);
        }
    }

    /**
     * Builds the {@code WHERE} shared by every read, appending each value to
     * {@code parameters} in the order its placeholder appears.
     *
     * <p>Clauses are appended only when a filter is actually set, and every
     * value is a bound {@code ?}. Both matter: a filter the caller did not ask
     * for must not narrow the result, and nothing a caller sends may reach the
     * query as text.</p>
     *
     * <p>The cursor comparison is written as
     * {@code event_time < ? OR (event_time = ? AND event_id < ?)} rather than as
     * a row-value comparison. The two are equivalent, and both let ClickHouse
     * use the sorting key for the range, but the expanded form does not depend
     * on how the driver renders a tuple parameter.</p>
     *
     * @param params receives the bind values, in placeholder order
     * @return the {@code WHERE} clause, beginning with {@code " WHERE "}
     */
    private static String whereClause(
            UUID userId,
            AnalyticsEventType eventType,
            Instant from,
            Instant to,
            AnalyticsCursor cursor,
            List<Object> params) {

        StringBuilder where = new StringBuilder(" WHERE user_id = ?");

        params.add(userId);

        if (eventType != null) {
            where.append(" AND event_type = ?");
            params.add(eventType.name());
        }

        if (from != null) {
            where.append(" AND event_time >= ?");
            params.add(Timestamp.from(from));
        }

        if (to != null) {
            where.append(" AND event_time <= ?");
            params.add(Timestamp.from(to));
        }

        if (cursor != null) {

            where.append(" AND (event_time < ? OR (event_time = ? AND event_id < ?))");

            // The same instant is bound twice: the query is keyed on event_time
            // first, and only rows sharing that exact instant fall through to
            // the id comparison.
            Timestamp boundary = Timestamp.from(cursor.eventTime());

            params.add(boundary);
            params.add(boundary);
            params.add(cursor.eventId());
        }

        return where.toString();
    }

    /**
     * Binds every value positionally, starting at index 1.
     *
     * <p>{@code setObject} rather than a typed setter per column: the caller
     * appends strings, timestamps and UUIDs to one list, and only the driver
     * knows how each maps onto ClickHouse's types.</p>
     */
    private static void bindAll(PreparedStatement statement, List<Object> params)
            throws SQLException {

        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
    }

    /**
     * Maps the current row of {@code COLUMNS} onto an {@link AnalyticsEventRow}.
     *
     * <p>UUID columns are read as strings and parsed rather than fetched with
     * {@code getObject(..., UUID.class)}: the driver's conversion support varies
     * by version and by column type, while the string form is stable.</p>
     *
     * <p>{@code event_time} is read with {@code getTimestamp}, the mirror of how
     * {@link #bind} writes it, so a value written here and read back here is the
     * same instant. Mixing in an {@code OffsetDateTime} read would reintroduce
     * the timezone question the write path already answers.</p>
     */
    private AnalyticsEventRow toRow(ResultSet rows) throws SQLException {

        Timestamp eventTime = rows.getTimestamp("event_time");

        return new AnalyticsEventRow(
                toUuid(rows.getString("event_id")),
                rows.getString("event_type"),
                toUuid(rows.getString("user_id")),
                rows.getString("session_id"),
                toUuid(rows.getString("bond_id")),
                (eventTime != null) ? eventTime.toInstant() : null,
                rows.getString("source"),
                rows.getString("page"),
                parseMetadata(rows.getString("metadata")));
    }

    /**
     * Parses the stored metadata JSON back into a map.
     *
     * <p>Metadata that cannot be parsed is reported as empty rather than
     * allowed to fail the page. It is supplementary detail on one row; losing
     * it is a smaller harm than refusing to show the admin the other nineteen
     * events, which is what a thrown exception here would amount to.</p>
     */
    private Map<String, Object> parseMetadata(String json) {

        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });

        } catch (RuntimeException e) {

            log.warn("Stored analytics metadata could not be parsed; returning it as empty. Value was: {}",
                    json, e);

            return Map.of();
        }
    }

    /** @return the parsed UUID, or {@code null} if the column held no value */
    private static UUID toUuid(String value) {
        return (value != null) ? UUID.fromString(value) : null;
    }

    /**
     * Binds one event to the nine placeholders of {@link #INSERT_SQL}.
     *
     * <p>The three columns that are not nullable in ClickHouse — {@code source},
     * {@code page} and {@code session_id} — are coalesced to empty strings. The
     * JDBC driver would otherwise send a NULL and the server would reject the
     * whole batch, losing every other event in it as well.</p>
     *
     * @param statement statement to bind into
     * @param event     the event supplying the values
     * @throws SQLException if the driver rejects a value
     */
    private void bind(PreparedStatement statement, AnalyticsEvent event) throws SQLException {

        statement.setObject(1, event.eventId());

        statement.setString(2, event.eventType().name());

        if (event.userId() != null) {
            statement.setObject(3, event.userId());
        } else {
            statement.setNull(3, Types.OTHER);
        }

        statement.setString(4, blankIfNull(event.sessionId()));

        if (event.bondId() != null) {
            statement.setObject(5, event.bondId());
        } else {
            statement.setNull(5, Types.OTHER);
        }

        statement.setTimestamp(6, Timestamp.from(event.eventTime()));

        statement.setString(7, blankIfNull(event.source()));

        statement.setString(8, blankIfNull(event.page()));

        statement.setString(9, toJson(event));
    }

    /**
     * Renders the event metadata as a JSON object.
     *
     * <p>Deliberately not {@code metadata.toString()}: that produces
     * {@code {key=value}}, which is not JSON and cannot be queried with
     * ClickHouse's {@code JSONExtract*} functions.</p>
     *
     * @param event the event whose metadata is rendered
     * @return a JSON object literal, never {@code null}
     */
    private String toJson(AnalyticsEvent event) {

        try {
            return objectMapper.writeValueAsString(event.metadata());

        } catch (RuntimeException e) {
            // Metadata the mapper cannot render must not cost us the rest of
            // the batch. Fall back to an empty object and say so.
            log.warn("Could not serialize metadata for analytics event {}; storing {{}}",
                    event.eventId(), e);

            return "{}";
        }
    }

    private static String blankIfNull(String value) {
        return (value != null) ? value : "";
    }
}
