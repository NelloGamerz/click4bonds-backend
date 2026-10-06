package com.click4bonds.app.Modules.Analytics.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * How a page boundary survives the round trip through a query parameter.
 *
 * <p>Pure unit tests: the cursor is the one piece of the paging scheme that can
 * lose data silently — a timestamp truncated to seconds would skip every event
 * sharing that second — so it is checked directly rather than through a query.
 */
class AnalyticsCursorTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-10-06T09:31:22.104Z");

    private static final UUID EVENT_ID = UUID.fromString("3f1c8a54-6d2e-4b0f-9c77-2ad5e6b1f4a9");

    @Test
    void shouldRoundTripBothPartsOfThePosition() {

        AnalyticsCursor decoded = AnalyticsCursor.decode(
                new AnalyticsCursor(EVENT_TIME, EVENT_ID).encode());

        assertEquals(EVENT_TIME, decoded.eventTime());
        assertEquals(EVENT_ID, decoded.eventId());
    }

    @Test
    void shouldKeepMillisecondPrecision() {

        // The column is DateTime64(3). Truncating to whole seconds would make
        // the next page start before rows it has already returned.
        Instant withMillis = Instant.parse("2026-10-06T09:31:22.104Z");

        AnalyticsCursor decoded = AnalyticsCursor.decode(
                new AnalyticsCursor(withMillis, EVENT_ID).encode());

        assertEquals(104, decoded.eventTime().toEpochMilli() % 1000);
    }

    @Test
    void shouldEncodeSomethingSafeToPutInAQueryString() {

        String encoded = new AnalyticsCursor(EVENT_TIME, EVENT_ID).encode();

        assertTrue(encoded.matches("[A-Za-z0-9_-]+"),
                "A cursor must survive a query string unescaped, was: " + encoded);
    }

    @Test
    void shouldNotBeReadableAsTheTimestampItHolds() {

        // Opaque on purpose: a caller who could read the position would be
        // tempted to construct one, and a constructed cursor is a request to
        // start paging somewhere the data may no longer support.
        String encoded = new AnalyticsCursor(EVENT_TIME, EVENT_ID).encode();

        assertTrue(!encoded.contains("2026") && !encoded.contains(EVENT_ID.toString()));
    }

    @Test
    void shouldRejectACursorThatIsNotBase64() {

        assertThrows(
                BadRequestException.class,
                () -> AnalyticsCursor.decode("not a cursor!!"));
    }

    @Test
    void shouldRejectBase64ThatIsNotACursor() {

        String encoded = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(BadRequestException.class, () -> AnalyticsCursor.decode(encoded));
    }

    @Test
    void shouldRejectACursorWithNoEventId() {

        String encoded = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("1791262800000:".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(BadRequestException.class, () -> AnalyticsCursor.decode(encoded));
    }

    @Test
    void shouldRejectACursorWhoseIdIsNotAUuid() {

        String encoded = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("1791262800000:not-a-uuid".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(BadRequestException.class, () -> AnalyticsCursor.decode(encoded));
    }
}
