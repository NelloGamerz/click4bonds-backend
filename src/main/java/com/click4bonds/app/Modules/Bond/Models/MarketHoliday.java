package com.click4bonds.app.Modules.Bond.Models;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A market holiday — a weekday on which the exchange does not settle.
 *
 * <p>Used by {@code BusinessDateService} to roll T+1/T+2 past a holiday as well
 * as a weekend. The year is stored alongside the date so the calendar can be
 * listed and audited year by year.</p>
 */
@Entity
@Table(name = "market_holidays", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_market_holidays_date",
                columnNames = "holiday_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Calendar year the holiday belongs to, e.g. 2026. */
    @Column(name = "holiday_year", nullable = false)
    private Integer year;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 255)
    private String name;
}
