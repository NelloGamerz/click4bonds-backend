package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.click4bonds.app.Modules.Bond.Repository.MarketHolidayRepository;

class BusinessDateServiceTest {

    private static final LocalDate HOLIDAY = LocalDate.of(2026, 1, 15);

    private MarketHolidayRepository marketHolidayRepository;

    private BusinessDateService businessDateService;

    @BeforeEach
    void setUp() {
        marketHolidayRepository = Mockito.mock(MarketHolidayRepository.class);
        Mockito.when(marketHolidayRepository.existsByDate(Mockito.any()))
                .thenReturn(false);
        Mockito.when(marketHolidayRepository.existsByDate(HOLIDAY))
                .thenReturn(true);
        businessDateService = new BusinessDateService(marketHolidayRepository);
    }

    @Test
    void tPlusOneAndTwoSkipWeekends() {

        assertBusinessDates("2026-11-02", "2026-11-03", "2026-11-04"); // Monday
        assertBusinessDates("2026-10-29", "2026-10-30", "2026-11-02"); // Thursday
        assertBusinessDates("2026-10-30", "2026-11-02", "2026-11-03"); // Friday
        assertBusinessDates("2026-10-31", "2026-11-02", "2026-11-03"); // Saturday
        assertBusinessDates("2026-11-01", "2026-11-02", "2026-11-03"); // Sunday
    }

    @Test
    void tPlusOneAndTwoSkipMarketHolidays() {

        // 15 Jan 2026 is a Thursday holiday, so T+1 rolls to Friday and T+2 to
        // the following Monday.
        assertBusinessDates("2026-01-14", "2026-01-16", "2026-01-19");

        // A deal struck on the holiday itself settles the next working day.
        assertBusinessDates("2026-01-15", "2026-01-16", "2026-01-19");
    }

    private void assertBusinessDates(
            String date,
            String expectedTPlusOne,
            String expectedTPlusTwo) {

        LocalDate dealDate = LocalDate.parse(date);

        assertEquals(LocalDate.parse(expectedTPlusOne),
                businessDateService.tPlusOne(dealDate),
                "T+1 from " + date);

        assertEquals(LocalDate.parse(expectedTPlusTwo),
                businessDateService.tPlusTwo(dealDate),
                "T+2 from " + date);
    }
}
