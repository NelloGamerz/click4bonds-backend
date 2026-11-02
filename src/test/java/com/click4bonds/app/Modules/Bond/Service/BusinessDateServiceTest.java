package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class BusinessDateServiceTest {

    private final BusinessDateService businessDateService = new BusinessDateService();

    @Test
    void tPlusOneAndTwoSkipWeekends() {

        assertBusinessDates("2026-11-02", "2026-11-03", "2026-11-04"); // Monday
        assertBusinessDates("2026-10-29", "2026-10-30", "2026-11-02"); // Thursday
        assertBusinessDates("2026-10-30", "2026-11-02", "2026-11-03"); // Friday
        assertBusinessDates("2026-10-31", "2026-11-02", "2026-11-03"); // Saturday
        assertBusinessDates("2026-11-01", "2026-11-02", "2026-11-03"); // Sunday
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
