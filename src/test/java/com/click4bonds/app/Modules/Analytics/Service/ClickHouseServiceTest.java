package com.click4bonds.app.Modules.Analytics.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Analytics.Exception.AnalyticsStorageException;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * How {@link ClickHouseService} turns events into a JDBC batch.
 *
 * <p>{@link DataSource} and the JDBC objects are mocked, so no ClickHouse
 * server is involved. The {@link ObjectMapper} is real, because the metadata
 * encoding is one of the things under test.</p>
 */
@ExtendWith(MockitoExtension.class)
class ClickHouseServiceTest {

    @Mock
    private DataSource clickHouseDataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    private ObjectMapper objectMapper;

    private ClickHouseService clickHouseService;

    @BeforeEach
    void setUp() throws SQLException {

        objectMapper = JsonMapper.builder().build();

        lenient().when(clickHouseDataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(statement);

        clickHouseService = new ClickHouseService(clickHouseDataSource, objectMapper);
    }

    @Test
    void shouldNotTouchClickHouseForAnEmptyBatch() {

        clickHouseService.insertBatch(List.of());

        verifyNoInteractions(clickHouseDataSource);
    }

    @Test
    void shouldNotTouchClickHouseForANullBatch() {

        clickHouseService.insertBatch(null);

        verifyNoInteractions(clickHouseDataSource);
    }

    @Test
    void shouldInsertASingleEventInOneBatch() throws SQLException {

        clickHouseService.insertBatch(List.of(event()));

        verify(statement, times(1)).addBatch();
        verify(statement, times(1)).executeBatch();
    }

    @Test
    void shouldAddEveryEventToTheBatch() throws SQLException {

        clickHouseService.insertBatch(List.of(
                event(), event(), event(), event(), event()));

        verify(statement, times(5)).addBatch();
        verify(statement, times(1)).executeBatch();
    }

    @Test
    void shouldBindTheEventIdAsAUuid() throws SQLException {

        AnalyticsEvent event = event();

        clickHouseService.insertBatch(List.of(event));

        verify(statement).setObject(1, event.eventId());
    }

    @Test
    void shouldBindEventTypeAndTimestamp() throws SQLException {

        AnalyticsEvent event = new AnalyticsEvent(
                UUID.randomUUID(),
                AnalyticsEventType.RFQ_SUBMIT,
                null,
                "session-1",
                null,
                Instant.parse("2026-09-15T10:15:30.250Z"),
                "WEB",
                "RFQ",
                Map.of());

        clickHouseService.insertBatch(List.of(event));

        verify(statement).setString(2, "RFQ_SUBMIT");
        verify(statement).setTimestamp(6, Timestamp.from(event.eventTime()));
    }

    @Test
    void shouldBindAPresentUserId() throws SQLException {

        UUID userID = UUID.randomUUID();
        clickHouseService.insertBatch(List.of(eventWithIds(UUID.randomUUID(), UUID.randomUUID())));

        verify(statement).setObject(3, userID);
    }

    @Test
    void shouldBindANullUserIdAsSqlNull() throws SQLException {

        clickHouseService.insertBatch(List.of(eventWithIds(null, UUID.randomUUID())));

        verify(statement).setNull(3, Types.BIGINT);
        verify(statement, never()).setLong(eq(3), anyInt());
    }

    @Test
    void shouldBindAPresentBondId() throws SQLException {

        clickHouseService.insertBatch(List.of(eventWithIds(UUID.randomUUID(), UUID.randomUUID())));

        verify(statement).setLong(5, 99L);
    }

    @Test
    void shouldBindANullBondIdAsSqlNull() throws SQLException {

        clickHouseService.insertBatch(List.of(eventWithIds(UUID.randomUUID(), null)));

        verify(statement).setNull(5, Types.BIGINT);
        verify(statement, never()).setLong(eq(5), anyInt());
    }

    @Test
    void shouldWriteMetadataAsJson() throws SQLException {

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filter", "AAA");
        metadata.put("sort", "yield-desc");
        metadata.put("pageSize", 25);

        clickHouseService.insertBatch(List.of(eventWithMetadata(metadata)));

        String json = captureMetadata();

        assertTrue(json.startsWith("{") && json.endsWith("}"),
                "metadata must be a JSON object, was: " + json);

        Map<String, Object> parsed = objectMapper.readValue(
                json, new TypeReference<Map<String, Object>>() {
                });

        assertEquals(metadata, parsed);
    }

    @Test
    void shouldNotWriteMetadataUsingMapToString() throws SQLException {

        clickHouseService.insertBatch(List.of(eventWithMetadata(Map.of("filter", "AAA"))));

        String json = captureMetadata();

        assertEquals("{\"filter\":\"AAA\"}", json,
                "Java's Map.toString() would render {filter=AAA}, which is not JSON");
    }

    @Test
    void shouldWriteAnEmptyJsonObjectForEmptyMetadata() throws SQLException {

        clickHouseService.insertBatch(List.of(eventWithMetadata(Map.of())));

        assertEquals("{}", captureMetadata());
    }

    @Test
    void shouldCoalesceNullTextColumnsToEmptyStrings() throws SQLException {

        // The three text columns are not nullable in ClickHouse. A NULL binding
        // would make the server reject the whole batch, not just this row.
        AnalyticsEvent event = new AnalyticsEvent(
                UUID.randomUUID(),
                AnalyticsEventType.LOGIN,
                null,
                null,
                null,
                Instant.now(),
                null,
                null,
                Map.of());

        clickHouseService.insertBatch(List.of(event));

        verify(statement).setString(4, "");
        verify(statement).setString(7, "");
        verify(statement).setString(8, "");
    }

    @Test
    void shouldWrapAnInsertFailure() throws SQLException {

        when(statement.executeBatch()).thenThrow(new SQLException("connection reset"));

        List<AnalyticsEvent> batch = List.of(event(), event());

        AnalyticsStorageException thrown = assertThrows(
                AnalyticsStorageException.class,
                () -> clickHouseService.insertBatch(batch));

        assertNotNull(thrown.getCause());
        assertEquals("connection reset", thrown.getCause().getMessage());
        assertTrue(thrown.getMessage().contains("2"),
                "The failure should say how many events were lost: " + thrown.getMessage());
    }

    @Test
    void shouldWrapAConnectionFailure() throws SQLException {

        when(clickHouseDataSource.getConnection())
                .thenThrow(new SQLException("ClickHouse refused the connection"));

        assertThrows(
                AnalyticsStorageException.class,
                () -> clickHouseService.insertBatch(List.of(event())));
    }

    @Test
    void shouldBindIntoASingleStatementForTheWholeBatch() throws SQLException {

        clickHouseService.insertBatch(List.of(event(), event(), event()));

        verify(connection, times(1)).prepareStatement(anyString());
    }

    /** @return the string bound to the metadata column (position 9) */
    private String captureMetadata() throws SQLException {

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(statement).setString(eq(9), captor.capture());

        return captor.getValue();
    }

    private static AnalyticsEvent event() {
        return eventWithIds(UUID.randomUUID(), UUID.randomUUID());
    }

    private static AnalyticsEvent eventWithIds(UUID userId, UUID bondId) {

        return new AnalyticsEvent(
                UUID.randomUUID(),
                AnalyticsEventType.BOND_VIEW,
                userId,
                "session-1",
                bondId,
                Instant.parse("2026-09-15T10:15:30Z"),
                "WEB",
                "BOND_DETAILS",
                Map.of());
    }

    private static AnalyticsEvent eventWithMetadata(Map<String, Object> metadata) {

        return new AnalyticsEvent(
                UUID.randomUUID(),
                AnalyticsEventType.BOND_VIEW,
                UUID.randomUUID(),
                "session-1",
                UUID.randomUUID(),
                Instant.parse("2026-09-15T10:15:30Z"),
                "WEB",
                "BOND_DETAILS",
                metadata);
    }
}
