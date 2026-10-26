package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Common.Exceptions.InternalServerException;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealReferenceSequenceRepository;

import lombok.RequiredArgsConstructor;

/**
 * Allocates {@code 2026/S/OCT/144}-style deal references: year, side of the
 * trade ({@code S} for our sale), month, and the deal's number within that
 * month.
 *
 * <p>The sequence comes from the {@code deal_reference_sequences} table and not
 * from a timestamp or an in-memory counter: a timestamp can repeat within the
 * same millisecond, and an in-memory counter restarts at 1 on every deploy.
 * Both would hand two customers the same reference. The database counter, taken
 * with a row lock, cannot.</p>
 *
 * <p>The counter is keyed by the first day of the month, because the reference
 * carries a month and no day: a daily counter would issue the same
 * year/month/number twice in one month.</p>
 *
 * <p>The counter is incremented inside the caller's transaction on purpose. If
 * the deal creation rolls back, the increment rolls back with it and the number
 * is issued again to the next deal — no gap, and still unique, because the deal
 * that would have held it was never written.</p>
 */
@Service
@RequiredArgsConstructor
public class DealReferenceGeneratorImpl implements DealReferenceGenerator {

    /** Side of the trade: every deal this application writes is our sale. */
    private static final String SALE = "S";

    /** Month in the reference, e.g. {@code OCT}. */
    private static final DateTimeFormatter MONTH_PART =
            DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private static final String SEQUENCE_FORMAT = "%d/%s/%s/%d";

    private final DealReferenceSequenceRepository sequenceRepository;

    @Override
    public String next(LocalDate sequenceDate) {

        /*
         * The reference has no day in it, so the counter is per month. Keying it
         * by the first of the month also makes the sequence independent of which
         * day the deal was struck.
         */
        LocalDate sequenceMonth = sequenceDate.withDayOfMonth(1);

        sequenceRepository.increment(sequenceMonth);

        Long lastValue = sequenceRepository.findLastValue(sequenceMonth);

        if (lastValue == null) {
            /*
             * The increment and the read are in the same transaction and the
             * row lock is held between them, so this cannot happen unless the
             * table was cleared concurrently. Fail loudly rather than issue a
             * reference from a guess.
             */
            throw new InternalServerException(
                    "Could not read the deal reference counter for "
                            + sequenceMonth);
        }

        return String.format(
                SEQUENCE_FORMAT,
                sequenceMonth.getYear(),
                SALE,
                sequenceMonth.format(MONTH_PART).toUpperCase(Locale.ENGLISH),
                lastValue);
    }
}
