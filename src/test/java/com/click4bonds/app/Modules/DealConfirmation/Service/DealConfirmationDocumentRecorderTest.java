package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.Document.Service.DocumentStorage;

/**
 * Linking a generated document back to its deal.
 *
 * <p>The interesting part is not the update: it is that the row records the
 * document's <em>address</em> while the generator hands over a <em>key</em>. A
 * row that persisted the key would still let this application find the letter,
 * but it would mean nothing to anyone reading the table — which is the whole
 * reason the conversion exists. So the assertion here is on the value that
 * reaches the database, not on the fact that an update happened.</p>
 */
@ExtendWith(MockitoExtension.class)
class DealConfirmationDocumentRecorderTest {

    private static final UUID DEAL_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final String KEY = "2026/09/DC-20260925-000001.pdf";

    private static final String LOCATION =
            "https://abc123.r2.cloudflarestorage.com/click4bonds-documents/" + KEY;

    @Mock
    private DealConfirmationRepository dealConfirmationRepository;

    @Mock
    private DocumentStorage documentStorage;

    @InjectMocks
    private DealConfirmationDocumentRecorder recorder;

    @Test
    void recordsTheAddressRatherThanTheKey() {

        when(documentStorage.locationOf(KEY)).thenReturn(LOCATION);
        when(dealConfirmationRepository.recordDocument(any(), any(), any(), any()))
                .thenReturn(1);

        recorder.record(DEAL_ID, KEY);

        verify(dealConfirmationRepository).recordDocument(
                eq(DEAL_ID),
                eq(DealConfirmationStatus.CONFIRMATION_GENERATED),
                eq(LOCATION),
                any(Instant.class));
    }

    @Test
    void swallowsADealThatIsNoLongerThere() {

        /*
         * The caller already treats a document failure as non-fatal, so throwing
         * here would add no signal. A deal that vanished between being written
         * and being documented is worth investigating, but it is not a reason to
         * report a failure for a purchase that succeeded.
         */
        when(documentStorage.locationOf(KEY)).thenReturn(LOCATION);
        when(dealConfirmationRepository.recordDocument(any(), any(), any(), any()))
                .thenReturn(0);

        assertDoesNotThrow(() -> recorder.record(DEAL_ID, KEY));
    }

    @Test
    void doesNothingWithoutADealId() {

        /*
         * Defensive: the caller passes the id from the response it just built. A
         * missing one would otherwise issue an UPDATE matching nothing, which is
         * harmless but hides a wiring mistake behind a warning about a missing
         * deal rather than a missing id.
         */
        assertDoesNotThrow(() -> recorder.record(null, KEY));

        verifyNoInteractions(dealConfirmationRepository);

        /*
         * And storage is not asked either. There is no row to address, so
         * demanding that the store be configured would turn a wiring mistake into
         * a storage failure.
         */
        verifyNoInteractions(documentStorage);
    }
}
