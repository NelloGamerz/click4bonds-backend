package com.click4bonds.app.Modules.Analytics.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Analytics.Config.ClickHouseConfig;
import com.click4bonds.app.Modules.Analytics.Exception.AnalyticsStorageException;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes batches of analytics events into
 * {@code click4bonds_analytics.analytics_events}.
 *
 * <p>This is the only class in the application that speaks ClickHouse SQL. It
 * is called from the Kafka consumer thread via {@code AnalyticsBatchService}
 * and never from a business service.</p>
 *
 * <p>The datasource is the analytics module's own and is reached through an
 * explicit qualifier — resolving {@code DataSource} by type here would risk
 * picking up the primary PostgreSQL datasource.</p>
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
            statement.setLong(3, event.userId());
        } else {
            statement.setNull(3, Types.BIGINT);
        }

        statement.setString(4, blankIfNull(event.sessionId()));

        if (event.bondId() != null) {
            statement.setLong(5, event.bondId());
        } else {
            statement.setNull(5, Types.BIGINT);
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
