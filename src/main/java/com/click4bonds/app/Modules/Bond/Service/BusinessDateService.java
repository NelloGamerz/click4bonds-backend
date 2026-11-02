package com.click4bonds.app.Modules.Bond.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Repository.MarketHolidayRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business-day arithmetic for settlement dates.
 *
 * <p>Weekends and the exchange holiday calendar are both excluded: T+1 is the
 * next working day and T+2 the second, rolling forward over any Saturday,
 * Sunday or configured market holiday.</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessDateService {

    private final MarketHolidayRepository marketHolidayRepository;

    public LocalDate tPlusOne(LocalDate date) {
        return plusBusinessDays(date, 1);
    }

    public LocalDate tPlusTwo(LocalDate date) {
        return plusBusinessDays(date, 2);
    }

    /**
     * Adds {@code businessDays} working days, skipping Saturdays, Sundays and
     * market holidays. Zero days returns the date unchanged.
     */
    public LocalDate plusBusinessDays(LocalDate date, int businessDays) {

        LocalDate result = date;
        int remaining = businessDays;

        while (remaining > 0) {
            result = result.plusDays(1);

            if (isBusinessDay(result)) {
                remaining--;
            }
        }

        return result;
    }

    private boolean isBusinessDay(LocalDate date) {

        DayOfWeek day = date.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        return !marketHolidayRepository.existsByDate(date);
    }
}
