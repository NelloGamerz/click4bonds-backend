package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Common.Exceptions.InternalServerException;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealReferenceSequenceRepository;

import lombok.RequiredArgsConstructor;

/**
 * Allocates {@code DC-YYYYMMDD-000001}-style deal references.
 *
 * <p>The sequence comes from the {@code deal_reference_sequences} table and not
 * from a timestamp or an in-memory counter: a timestamp can repeat within the
 * same millisecond, and an in-memory counter restarts at 1 on every deploy.
 * Both would hand two customers the same reference. The database counter, taken
 * with a row lock, cannot.</p>
 *
 * <p>The counter is incremented inside the caller's transaction on purpose. If
 * the deal creation rolls back, the increment rolls back with it and the number
 * is issued again to the next deal — no gap, and still unique, because the deal
 * that would have held it was never written.</p>
 */
@Service
@RequiredArgsConstructor
public class DealReferenceGeneratorImpl implements DealReferenceGenerator {

    private static final String PREFIX = "DC";

    /** Sequence part is zero padded so references sort in issue order as text. */
    private static final DateTimeFormatter DATE_PART =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String SEQUENCE_FORMAT =
            PREFIX + "-%s-%06d";

    private final DealReferenceSequenceRepository sequenceRepository;

    @Override
    public String next(LocalDate sequenceDate) {

        sequenceRepository.increment(sequenceDate);

        Long lastValue = sequenceRepository.findLastValue(sequenceDate);

        if (lastValue == null) {
            /*
             * The increment and the read are in the same transaction and the
             * row lock is held between them, so this cannot happen unless the
             * table was cleared concurrently. Fail loudly rather than issue a
             * reference from a guess.
             */
            throw new InternalServerException(
                    "Could not read the deal reference counter for "
                            + sequenceDate);
        }

        return String.format(
                SEQUENCE_FORMAT,
                sequenceDate.format(DATE_PART),
                lastValue);
    }
}
