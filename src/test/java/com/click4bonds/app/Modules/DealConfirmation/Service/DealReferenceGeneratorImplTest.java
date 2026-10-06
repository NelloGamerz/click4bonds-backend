package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Common.Exceptions.InternalServerException;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealReferenceSequenceRepository;

/**
 * Shape and safety of the customer-facing deal reference.
 */
@ExtendWith(MockitoExtension.class)
class DealReferenceGeneratorImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 22);

    @Mock
    private DealReferenceSequenceRepository sequenceRepository;

    @InjectMocks
    private DealReferenceGeneratorImpl generator;

    @Test
    void formatsTheReferenceAsDayThenSequence() {

        givenCounterReads(1L);

        assertEquals("DC-20260922-000001", generator.next(DAY));
    }

    @Test
    void padsTheSequenceSoReferencesSortAsText() {

        givenCounterReads(42L);

        String reference = generator.next(DAY);

        assertEquals("DC-20260922-000042", reference);

        // Text ordering must match issue ordering, or a sorted list would be wrong.
        givenCounterReads(1000L);
        assertEquals("DC-20260922-001000", generator.next(DAY));
    }

    @Test
    void takesTheSequenceFromTheCounterItJustIncremented() {

        givenCounterReads(7L);

        generator.next(DAY);

        /*
         * The counter must be incremented before it is read, and both must happen
         * for the same day — that pair is what makes the reference unique under
         * concurrent deals.
         */
        verify(sequenceRepository).increment(DAY);
        verify(sequenceRepository).findLastValue(DAY);
    }

    @Test
    void failsLoudlyWhenTheCounterCannotBeReadBack() {

        // Increment said it worked but the value is gone: issuing a guess here
        // could hand two deals the same reference.
        when(sequenceRepository.findLastValue(any())).thenReturn(null);

        assertThrows(InternalServerException.class, () -> generator.next(DAY));
    }

    private void givenCounterReads(long value) {
        when(sequenceRepository.findLastValue(DAY)).thenReturn(value);
    }
}
