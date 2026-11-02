package com.click4bonds.app.Modules.Bond.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

/**
 * Business-day arithmetic for settlement dates.
 *
 * <p>Only weekends are excluded: T+1 is the next working day and T+2 the second,
 * rolling a Saturday or Sunday forward to Monday.</p>
 */
@Service
public class BusinessDateService {

    public LocalDate tPlusOne(LocalDate date) {
        return plusBusinessDays(date, 1);
    }

    public LocalDate tPlusTwo(LocalDate date) {
        return plusBusinessDays(date, 2);
    }

    /**
     * Adds {@code businessDays} working days, skipping Saturdays and Sundays.
     * Zero days returns the date unchanged.
     *
     * shortcut: market holidays are not considered; add an exchange calendar
     * when the business needs them.
     */
    public LocalDate plusBusinessDays(LocalDate date, int businessDays) {

        LocalDate result = date;
        int remaining = businessDays;

        while (remaining > 0) {
            result = result.plusDays(1);

            if (result.getDayOfWeek() != DayOfWeek.SATURDAY
                    && result.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }

        return result;
    }
}
