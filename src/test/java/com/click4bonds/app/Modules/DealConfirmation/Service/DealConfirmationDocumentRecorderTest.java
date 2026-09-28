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

/**
 * Linking a generated document back to its deal.
 */
@ExtendWith(MockitoExtension.class)
class DealConfirmationDocumentRecorderTest {

    private static final UUID DEAL_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private DealConfirmationRepository dealConfirmationRepository;

    @InjectMocks
    private DealConfirmationDocumentRecorder recorder;

    @Test
    void flipsTheDealToConfirmationGeneratedAndKeepsTheKey() {

        when(dealConfirmationRepository.recordDocument(any(), any(), any(), any()))
                .thenReturn(1);

        recorder.record(DEAL_ID, "2026/09/DC-20260925-000001.pdf");

        verify(dealConfirmationRepository).recordDocument(
                eq(DEAL_ID),
                eq(DealConfirmationStatus.CONFIRMATION_GENERATED),
                eq("2026/09/DC-20260925-000001.pdf"),
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
        when(dealConfirmationRepository.recordDocument(any(), any(), any(), any()))
                .thenReturn(0);

        assertDoesNotThrow(
                () -> recorder.record(DEAL_ID, "2026/09/DC-20260925-000001.pdf"));
    }

    @Test
    void doesNothingWithoutADealId() {

        /*
         * Defensive: the caller passes the id from the response it just built. A
         * missing one would otherwise issue an UPDATE matching nothing, which is
         * harmless but hides a wiring mistake behind a warning about a missing
         * deal rather than a missing id.
         */
        assertDoesNotThrow(
                () -> recorder.record(null, "2026/09/DC-20260925-000001.pdf"));

        verifyNoInteractions(dealConfirmationRepository);
    }
}
