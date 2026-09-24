//package com.click4bonds.app.Modules.Analytics.Service;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertNull;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.doThrow;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.verifyNoInteractions;
//
//import java.time.Instant;
//import java.util.LinkedHashMap;
//import java.util.Map;
//import java.util.UUID;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;
//import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
//import com.click4bonds.app.Modules.Analytics.Producer.AnalyticsEventProducer;
//
/// **
// * What {@link AnalyticsService} does with the arguments it is handed.
// *
// * <p>The producer is mocked, so this covers building the event and nothing
// * about Kafka.</p>
// */
//@ExtendWith(MockitoExtension.class)
//class AnalyticsServiceTest {
//
//    @Mock
//    private AnalyticsEventProducer producer;
//
//    private AnalyticsService analyticsService;
//
//    @BeforeEach
//    void setUp() {
//        analyticsService = new AnalyticsService(producer);
//    }
//
//    @Test
//    void shouldPublishExactlyOneEventPerTrackCall() {
//
//        analyticsService.track(
//                AnalyticsEventType.BOND_VIEW, UUID.randomUUID(), "session-1", UUID.randomUUID(),
//                "WEB", "BOND_DETAILS", Map.of());
//
//        verify(producer, times(1)).publish(any(AnalyticsEvent.class));
//    }
//
//    @Test
//    void shouldGenerateAnEventId() {
//
//        analyticsService.track(
//                AnalyticsEventType.LOGIN, UUID.randomUUID(), "session-1", null,
//                "WEB", "LOGIN", Map.of());
//
//        assertNotNull(capture().eventId());
//    }
//
//    @Test
//    void shouldGenerateADistinctEventIdPerCall() {
//
//        analyticsService.track(
//                AnalyticsEventType.BOND_VIEW, UUID.randomUUID(), "session-1", UUID.randomUUID(),
//                "WEB", "BOND_DETAILS", Map.of());
//
//        analyticsService.track(
//                AnalyticsEventType.BOND_VIEW, UUID.randomUUID(), "session-1", UUID.randomUUID(),
//                "WEB", "BOND_DETAILS", Map.of());
//
//        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
//
//        verify(producer, times(2)).publish(captor.capture());
//
//        assertNotEquals(
//                captor.getAllValues().get(0).eventId(),
//                captor.getAllValues().get(1).eventId(),
//                "Two events must never share an id, or ClickHouse would collapse them");
//    }
//
//    @Test
//    void shouldStampTheEventTimeAtTheMomentOfTheCall() {
//
//        Instant before = Instant.now();
//
//        analyticsService.track(
//                AnalyticsEventType.SEARCH, null, "session-1", null,
//                "WEB", "SEARCH", Map.of());
//
//        Instant after = Instant.now();
//
//        Instant eventTime = capture().eventTime();
//
//        assertNotNull(eventTime);
//        assertFalse(eventTime.isBefore(before), "Event time must not predate the call");
//        assertFalse(eventTime.isAfter(after), "Event time must not postdate the call");
//    }
//
//    @Test
//    void shouldStoreTheRequestedEventType() {
//
//        analyticsService.track(
//                AnalyticsEventType.RFQ_SUBMIT, UUID.randomUUID(), "session-1", UUID.randomUUID(),
//                "WEB", "RFQ", Map.of());
//
//        assertEquals(AnalyticsEventType.RFQ_SUBMIT, capture().eventType());
//    }
//
//    @Test
//    void shouldCarryUserSessionAndBondIds() {
//
//        analyticsService.track(
//                AnalyticsEventType.ORDER_SUBMIT, UUID.randomUUID(), "session-xyz", UUID.randomUUID(),
//                "WEB", "ORDER", Map.of());
//
//        AnalyticsEvent event = capture();
//
//        assertEquals(42L, event.userId());
//        assertEquals("session-xyz", event.sessionId());
//        assertEquals(99L, event.bondId());
//    }
//
//    @Test
//    void shouldKeepUserIdAndBondIdNullForAnonymousTraffic() {
//
//        analyticsService.track(
//                AnalyticsEventType.BOND_VIEW, null, "session-anon", null,
//                "WEB", "BOND_DETAILS", Map.of());
//
//        AnalyticsEvent event = capture();
//
//        assertNull(event.userId(), "Anonymous traffic must stay null, not become 0");
//        assertNull(event.bondId(), "An event without a bond must stay null");
//    }
//
//    @Test
//    void shouldCarrySourceAndPage() {
//
//        analyticsService.track(
//                AnalyticsEventType.BOND_CLICK, UUID.randomUUID(), "session-1", UUID.randomUUID(),
//                "MOBILE", "BOND_LIST", Map.of());
//
//        AnalyticsEvent event = capture();
//
//        assertEquals("MOBILE", event.source());
//        assertEquals("BOND_LIST", event.page());
//    }
//
//    @Test
//    void shouldPreserveMetadata() {
//
//        Map<String, Object> metadata = new LinkedHashMap<>();
//        metadata.put("filter", "AAA");
//        metadata.put("sort", "yield-desc");
//        metadata.put("pageSize", 25);
//
//        analyticsService.track(
//                AnalyticsEventType.SEARCH, UUID.randomUUID(), "session-1", null,
//                "WEB", "SEARCH", metadata);
//
//        assertEquals(metadata, capture().metadata());
//    }
//
//    @Test
//    void shouldReplaceNullMetadataWithAnEmptyMap() {
//
//        analyticsService.track(
//                AnalyticsEventType.LOGOUT, UUID.randomUUID(), "session-1", null,
//                "WEB", "LOGOUT", null);
//
//        assertEquals(Map.of(), capture().metadata());
//    }
//
//    @Test
//    void shouldIgnoreAnEventWithoutAType() {
//
//        analyticsService.track(
//                null, UUID.randomUUID(), "session-1", UUID.randomUUID(), "WEB", "BOND_DETAILS", Map.of());
//
//        verifyNoInteractions(producer);
//    }
//
//    @Test
//    void shouldNotLetAProducerFailureReachTheCaller() {
//
//        doThrow(new RuntimeException("broker unreachable"))
//                .when(producer).publish(any(AnalyticsEvent.class));
//
//        // The point of the test: this must not throw. A bond page still has to
//        // render when the analytics pipeline is down.
//        analyticsService.track(
//                AnalyticsEventType.BOND_VIEW, UUID.randomUUID(), "session-1", UUID.randomUUID(),
//                "WEB", "BOND_DETAILS", Map.of());
//
//        verify(producer).publish(any(AnalyticsEvent.class));
//    }
//
//    /** @return the single event handed to the mocked producer */
//    private AnalyticsEvent capture() {
//
//        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
//
//        verify(producer).publish(captor.capture());
//
//        return captor.getValue();
//    }
//}


package com.click4bonds.app.Modules.Analytics.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Producer.AnalyticsEventProducer;

/**
 * What {@link AnalyticsService} does with the arguments it is handed.
 *
 * <p>The producer is mocked, so this covers building the event and nothing
 * about Kafka.</p>
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsEventProducer producer;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(producer);
    }

    @Test
    void shouldPublishExactlyOneEventPerTrackCall() {

        analyticsService.track(AnalyticsEventType.BOND_VIEW, UUID.randomUUID(), "session-1", UUID.randomUUID(), "WEB", "BOND_DETAILS", Map.of());

        verify(producer, times(1)).publish(any(AnalyticsEvent.class));
    }

    @Test
    void shouldGenerateAnEventId() {

        analyticsService.track(AnalyticsEventType.LOGIN, UUID.randomUUID(), "session-1", null, "WEB", "LOGIN", Map.of());

        assertNotNull(capture().eventId());
    }

    @Test
    void shouldGenerateADistinctEventIdPerCall() {

        analyticsService.track(AnalyticsEventType.BOND_VIEW, UUID.randomUUID(), "session-1", UUID.randomUUID(), "WEB", "BOND_DETAILS", Map.of());

        analyticsService.track(AnalyticsEventType.BOND_VIEW, UUID.randomUUID(), "session-1", UUID.randomUUID(), "WEB", "BOND_DETAILS", Map.of());

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);

        verify(producer, times(2)).publish(captor.capture());

        assertNotEquals(captor.getAllValues().get(0).eventId(), captor.getAllValues().get(1).eventId(), "Two events must never share an id, or ClickHouse would collapse them");
    }

    @Test
    void shouldStampTheEventTimeAtTheMomentOfTheCall() {

        Instant before = Instant.now();

        analyticsService.track(AnalyticsEventType.SEARCH, null, "session-1", null, "WEB", "SEARCH", Map.of());

        Instant after = Instant.now();

        Instant eventTime = capture().eventTime();

        assertNotNull(eventTime);
        assertFalse(eventTime.isBefore(before), "Event time must not predate the call");
        assertFalse(eventTime.isAfter(after), "Event time must not postdate the call");
    }

    @Test
    void shouldStoreTheRequestedEventType() {

        analyticsService.track(AnalyticsEventType.RFQ_SUBMIT, UUID.randomUUID(), "session-1", UUID.randomUUID(), "WEB", "RFQ", Map.of());

        assertEquals(AnalyticsEventType.RFQ_SUBMIT, capture().eventType());
    }

    @Test
    void shouldCarryUserSessionAndBondIds() {

        UUID userId = UUID.randomUUID();
        UUID bondId = UUID.randomUUID();

        analyticsService.track(AnalyticsEventType.ORDER_SUBMIT, userId, "session-xyz", bondId, "WEB", "ORDER", Map.of());

        AnalyticsEvent event = capture();

        assertEquals(userId, event.userId());
        assertEquals("session-xyz", event.sessionId());
        assertEquals(bondId, event.bondId());
    }

    @Test
    void shouldCarryRealUserAndBondIds() {

        UUID userId = UUID.fromString("f74823bf-3486-45b1-8b02-411299a87235");

        UUID bondId = UUID.fromString("4f6d7301-0df8-488d-a882-277987a77a7e");

        analyticsService.track(AnalyticsEventType.BOND_VIEW, userId, "session-real-user", bondId, "WEB", "BOND_DETAILS", Map.of("source", "bond-details", "action", "view"));

        AnalyticsEvent event = capture();

        assertEquals(userId, event.userId(), "Analytics event must contain the expected user UUID");

        assertEquals(bondId, event.bondId(), "Analytics event must contain the expected bond UUID");

        assertEquals(AnalyticsEventType.BOND_VIEW, event.eventType());

        assertEquals("session-real-user", event.sessionId());

        assertEquals("WEB", event.source());

        assertEquals("BOND_DETAILS", event.page());
    }

    @Test
    void shouldKeepUserIdAndBondIdNullForAnonymousTraffic() {

        analyticsService.track(AnalyticsEventType.BOND_VIEW, null, "session-anon", null, "WEB", "BOND_DETAILS", Map.of());

        AnalyticsEvent event = capture();

        assertNull(event.userId(), "Anonymous traffic must stay null, not become 0");

        assertNull(event.bondId(), "An event without a bond must stay null");
    }

    @Test
    void shouldCarrySourceAndPage() {

        analyticsService.track(AnalyticsEventType.BOND_CLICK, UUID.randomUUID(), "session-1", UUID.randomUUID(), "MOBILE", "BOND_LIST", Map.of());

        AnalyticsEvent event = capture();

        assertEquals("MOBILE", event.source());
        assertEquals("BOND_LIST", event.page());
    }

    @Test
    void shouldPreserveMetadata() {

        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put("filter", "AAA");
        metadata.put("sort", "yield-desc");
        metadata.put("pageSize", 25);

        analyticsService.track(AnalyticsEventType.SEARCH, UUID.randomUUID(), "session-1", null, "WEB", "SEARCH", metadata);

        assertEquals(metadata, capture().metadata());
    }

    @Test
    void shouldReplaceNullMetadataWithAnEmptyMap() {

        analyticsService.track(AnalyticsEventType.LOGOUT, UUID.randomUUID(), "session-1", null, "WEB", "LOGOUT", null);

        assertEquals(Map.of(), capture().metadata());
    }

    @Test
    void shouldIgnoreAnEventWithoutAType() {

        analyticsService.track(null, UUID.randomUUID(), "session-1", UUID.randomUUID(), "WEB", "BOND_DETAILS", Map.of());

        verifyNoInteractions(producer);
    }

    @Test
    void shouldNotLetAProducerFailureReachTheCaller() {

        doThrow(new RuntimeException("broker unreachable")).when(producer).publish(any(AnalyticsEvent.class));

        // The point of the test: this must not throw. A bond page still has to
        // render when the analytics pipeline is down.
        analyticsService.track(AnalyticsEventType.BOND_VIEW, UUID.randomUUID(), "session-1", UUID.randomUUID(), "WEB", "BOND_DETAILS", Map.of());

        verify(producer).publish(any(AnalyticsEvent.class));
    }

    /**
     * @return the single event handed to the mocked producer
     */
    private AnalyticsEvent capture() {

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);

        verify(producer).publish(captor.capture());

        return captor.getValue();
    }
}