package com.click4bonds.app.Modules.DealConfirmation.Model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Daily counter behind {@code DealConfirmation.dealReference}.
 *
 * <p>One row per day that has issued at least one reference; {@code lastValue}
 * is the most recent sequence number handed out for {@link #sequenceDate}. It
 * exists so the reference number comes from a persisted counter rather than an
 * in-memory one, which would restart at 1 on every deploy and collide.</p>
 *
 * <p>The row is never read and modified separately: the repository increments it
 * with a single {@code INSERT ... ON CONFLICT DO UPDATE} statement, which is
 * what makes it safe when several deals are created at the same instant.</p>
 */
@Entity
@Table(name = "deal_reference_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealReferenceSequence {

    /** The day whose references this counter issues; also the primary key. */
    @Id
    @Column(name = "sequence_date", nullable = false, updatable = false)
    private LocalDate sequenceDate;

    /** Highest sequence number issued so far for {@link #sequenceDate}. */
    @Column(name = "last_value", nullable = false)
    private Long lastValue;
}
