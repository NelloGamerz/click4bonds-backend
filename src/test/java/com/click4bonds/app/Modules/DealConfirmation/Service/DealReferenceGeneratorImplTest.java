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

    /** The counter is keyed by the month, because the reference has no day. */
    private static final LocalDate MONTH = LocalDate.of(2026, 9, 1);

    @Mock
    private DealReferenceSequenceRepository sequenceRepository;

    @InjectMocks
    private DealReferenceGeneratorImpl generator;

    @Test
    void formatsTheReferenceAsYearSaleMonthAndSequence() {

        givenCounterReads(1L);

        assertEquals("2026/S/SEP/1", generator.next(DAY));
    }

    @Test
    void takesTheSequenceForTheWholeMonthRatherThanTheDay() {

        givenCounterReads(42L);

        String reference = generator.next(DAY);

        assertEquals("2026/S/SEP/42", reference);

        /*
         * The reference carries no day, so every deal in September must draw on
         * the same counter — otherwise two deals on different days of the month
         * could be issued the same year/S/SEP/number.
         */
        assertEquals("2026/S/SEP/42", generator.next(LocalDate.of(2026, 9, 30)));
    }

    @Test
    void takesTheSequenceFromTheCounterItJustIncremented() {

        givenCounterReads(7L);

        generator.next(DAY);

        /*
         * The counter must be incremented before it is read, and both must happen
         * for the same month — that pair is what makes the reference unique under
         * concurrent deals.
         */
        verify(sequenceRepository).increment(MONTH);
        verify(sequenceRepository).findLastValue(MONTH);
    }

    @Test
    void failsLoudlyWhenTheCounterCannotBeReadBack() {

        // Increment said it worked but the value is gone: issuing a guess here
        // could hand two deals the same reference.
        when(sequenceRepository.findLastValue(any())).thenReturn(null);

        assertThrows(InternalServerException.class, () -> generator.next(DAY));
    }

    private void givenCounterReads(long value) {
        when(sequenceRepository.findLastValue(MONTH)).thenReturn(value);
    }
}
