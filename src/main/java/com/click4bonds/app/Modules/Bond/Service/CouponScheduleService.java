package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Models.Bond;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponScheduleService {

    private final CouponDateGenerator couponDateGenerator;

    public CouponSchedule resolve(Bond bond, LocalDate calculationDate) {
        List<LocalDate> scheduleDates = couponDateGenerator.generate(
                bond,
            calculationDate.minusYears(1).minusDays(1));

        if (scheduleDates == null) {
            return new CouponSchedule(null, null);
        }

        Optional<LocalDate> previous = scheduleDates.stream()
                .filter(date -> !date.isAfter(calculationDate))
                .max(Comparator.naturalOrder());
        Optional<LocalDate> next = scheduleDates.stream()
                .filter(date -> date.isAfter(calculationDate))
                .min(Comparator.naturalOrder());

        return new CouponSchedule(previous.orElse(null), next.orElse(null));
    }

    public record CouponSchedule(LocalDate previous, LocalDate next) {
    }
}