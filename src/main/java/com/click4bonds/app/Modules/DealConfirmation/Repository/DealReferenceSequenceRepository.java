package com.click4bonds.app.Modules.DealConfirmation.Repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.click4bonds.app.Modules.DealConfirmation.Model.DealReferenceSequence;

public interface DealReferenceSequenceRepository
        extends JpaRepository<DealReferenceSequence, LocalDate> {

    /**
     * Bumps the counter for {@code sequenceDate} and leaves it locked for the
     * rest of the current transaction.
     *
     * <p>Written as raw SQL because the increment has to be one statement: a
     * read-then-write in Java lets two concurrent deals read the same counter
     * and issue the same reference. {@code ON CONFLICT DO UPDATE} takes the row
     * lock (creating the row on the day's first deal), so a second transaction
     * calling this blocks until the first one commits and then increments on
     * top of it.</p>
     *
     * <p>Callers must read the new value back with
     * {@link #findLastValue(LocalDate)}. Because this transaction holds the
     * lock, the value read back is the one this call produced — no other
     * transaction can have incremented in between.</p>
     *
     * @param sequenceDate day whose counter is being incremented
     * @return number of rows affected — always 1
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO deal_reference_sequences (sequence_date, last_value)
            VALUES (:sequenceDate, 1)
            ON CONFLICT (sequence_date)
            DO UPDATE SET last_value = deal_reference_sequences.last_value + 1
            """, nativeQuery = true)
    int increment(@Param("sequenceDate") LocalDate sequenceDate);

    /**
     * Reads the counter written by {@link #increment(LocalDate)}.
     *
     * <p>Must be called inside the same transaction as the increment that is
     * being read; outside it, the value is the current highest issued one
     * instead of this caller's.</p>
     */
    @Query(value = """
            SELECT last_value
            FROM deal_reference_sequences
            WHERE sequence_date = :sequenceDate
            """, nativeQuery = true)
    Long findLastValue(@Param("sequenceDate") LocalDate sequenceDate);
}
