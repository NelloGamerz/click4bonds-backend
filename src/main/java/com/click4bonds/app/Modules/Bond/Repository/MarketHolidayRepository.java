package com.click4bonds.app.Modules.Bond.Repository;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Bond.Models.MarketHoliday;

public interface MarketHolidayRepository
        extends JpaRepository<MarketHoliday, UUID> {

    boolean existsByHolidayDate(LocalDate holidayDate);
}
