package com.click4bonds.app.Modules.Bond.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class CouponDateGenerator {

    /*
     * ============================================================
     * PATTERNS
     * ============================================================
     */

    /**
     * Matches:
     *
     * 09/02-09/08
     * 04/04-04/10
     * 26/03-26/09
     *
     * Groups:
     *
     * 1 = first day
     * 2 = first month
     * 3 = second day
     * 4 = second month
     */
    private static final Pattern MONTH_DAY_RANGE_PATTERN =
            Pattern.compile(
                    "^\\s*(\\d{1,2})/(\\d{1,2})\\s*-\\s*(\\d{1,2})/(\\d{1,2})\\s*$"
            );

    /**
     * Matches:
     *
     * 15/10 Ann
     * 23/01 Ann
     * 01/08 Ann
     *
     * Groups:
     *
     * 1 = day
     * 2 = month
     */
    private static final Pattern ANNUAL_PATTERN =
            Pattern.compile(
                    "^\\s*(\\d{1,2})/(\\d{1,2})\\s+Ann\\.?\\s*$",
                    Pattern.CASE_INSENSITIVE
            );

    /**
     * Matches:
     *
     * 31st of every month
     * 23rd of every month
     * 17th of every month
     * 15th of Every Month
     * 5th of Every Month
     * 22nd of Every Month
     *
     * Group:
     *
     * 1 = day
     */
    private static final Pattern MONTHLY_PATTERN =
            Pattern.compile(
                    "^\\s*(\\d{1,2})(?:st|nd|rd|th)?\\s+of\\s+every\\s+month\\s*$",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * ============================================================
     * PUBLIC METHOD
     * ============================================================
     */

    /**
     * Generates all future coupon dates for a bond.
     *
     * calculationDate:
     * The date from which YTM is being calculated.
     *
     * maturityDate:
     * Taken from bond.getMaturityDate().
     *
     * Only dates:
     *
     *     > calculationDate
     *     <= maturityDate
     *
     * are returned.
     */
    public List<LocalDate> generate(
            Bond bond,
            LocalDate calculationDate
    ) {

        validateInput(bond, calculationDate);

        String description =
                normalizeDescription(
                        bond.getIpDateDescription()
                );

        LocalDate maturityDate =
                bond.getMaturityDate();

        /*
         * --------------------------------------------------------
         * 1. Semi-annual / date range
         * --------------------------------------------------------
         *
         * Example:
         *
         * 09/02-09/08
         *
         * Means:
         *
         * 09 February
         * 09 August
         */
        Matcher rangeMatcher =
                MONTH_DAY_RANGE_PATTERN.matcher(description);

        if (rangeMatcher.matches()) {

            return generateMonthDayRange(
                    rangeMatcher,
                    calculationDate,
                    maturityDate
            );
        }

        /*
         * --------------------------------------------------------
         * 2. Annual
         * --------------------------------------------------------
         *
         * Example:
         *
         * 15/10 Ann
         *
         * Means:
         *
         * 15 October every year.
         */
        Matcher annualMatcher =
                ANNUAL_PATTERN.matcher(description);

        if (annualMatcher.matches()) {

            return generateAnnualDates(
                    annualMatcher,
                    calculationDate,
                    maturityDate
            );
        }

        /*
         * --------------------------------------------------------
         * 3. Monthly
         * --------------------------------------------------------
         *
         * Example:
         *
         * 31st of every month
         */
        Matcher monthlyMatcher =
                MONTHLY_PATTERN.matcher(description);

        if (monthlyMatcher.matches()) {

            return generateMonthlyDates(
                    monthlyMatcher,
                    calculationDate,
                    maturityDate
            );
        }

        /*
         * --------------------------------------------------------
         * Unsupported format
         * --------------------------------------------------------
         */

        throw new IllegalArgumentException(
                "Unsupported IP date description: "
                        + bond.getIpDateDescription()
        );
    }

    /*
     * ============================================================
     * RANGE FORMAT
     * ============================================================
     */

    /**
     * Generates dates for:
     *
     * 09/02-09/08
     * 04/04-04/10
     * 26/03-26/09
     *
     * Example:
     *
     * calculationDate = 24-Aug-2026
     * maturityDate    = 09-Aug-2029
     *
     * description     = 09/02-09/08
     *
     * Result:
     *
     * 09-Feb-2027
     * 09-Aug-2027
     * 09-Feb-2028
     * 09-Aug-2028
     * 09-Feb-2029
     * 09-Aug-2029
     */
    private List<LocalDate> generateMonthDayRange(
            Matcher matcher,
            LocalDate calculationDate,
            LocalDate maturityDate
    ) {

        int firstDay =
                Integer.parseInt(matcher.group(1));

        int firstMonth =
                Integer.parseInt(matcher.group(2));

        int secondDay =
                Integer.parseInt(matcher.group(3));

        int secondMonth =
                Integer.parseInt(matcher.group(4));

        validateMonth(firstMonth);
        validateMonth(secondMonth);

        validateDay(firstDay);
        validateDay(secondDay);

        List<LocalDate> dates =
                new ArrayList<>();

        int startYear =
                calculationDate.getYear();

        int endYear =
                maturityDate.getYear();

        for (int year = startYear;
             year <= endYear;
             year++) {

            LocalDate firstDate =
                    createSafeDate(
                            year,
                            firstMonth,
                            firstDay
                    );

            if (firstDate != null
                    && isValidFutureDate(
                            firstDate,
                            calculationDate,
                            maturityDate
                    )) {

                dates.add(firstDate);
            }

            LocalDate secondDate =
                    createSafeDate(
                            year,
                            secondMonth,
                            secondDay
                    );

            if (secondDate != null
                    && isValidFutureDate(
                            secondDate,
                            calculationDate,
                            maturityDate
                    )) {

                dates.add(secondDate);
            }
        }

        dates.sort(Comparator.naturalOrder());

        return dates;
    }

    /*
     * ============================================================
     * ANNUAL FORMAT
     * ============================================================
     */

    /**
     * Generates dates for:
     *
     * 15/10 Ann
     *
     * Example:
     *
     * calculationDate = 24-Aug-2026
     * maturityDate    = 15-Oct-2030
     *
     * Result:
     *
     * 15-Oct-2026
     * 15-Oct-2027
     * 15-Oct-2028
     * 15-Oct-2029
     * 15-Oct-2030
     */
    private List<LocalDate> generateAnnualDates(
            Matcher matcher,
            LocalDate calculationDate,
            LocalDate maturityDate
    ) {

        int day =
                Integer.parseInt(matcher.group(1));

        int month =
                Integer.parseInt(matcher.group(2));

        validateMonth(month);
        validateDay(day);

        List<LocalDate> dates =
                new ArrayList<>();

        int startYear =
                calculationDate.getYear();

        int endYear =
                maturityDate.getYear();

        for (int year = startYear;
             year <= endYear;
             year++) {

            LocalDate date =
                    createSafeDate(
                            year,
                            month,
                            day
                    );

            if (date != null
                    && isValidFutureDate(
                            date,
                            calculationDate,
                            maturityDate
                    )) {

                dates.add(date);
            }
        }

        return dates;
    }

    /*
     * ============================================================
     * MONTHLY FORMAT
     * ============================================================
     */

    /**
     * Generates dates for:
     *
     * 31st of every month
     * 23rd of every month
     * 15th of every month
     *
     * Important:
     *
     * For 31st of every month:
     *
     * January   -> 31
     * February  -> 28/29
     * March     -> 31
     * April     -> 30
     *
     * We therefore clamp the requested day to
     * the last valid day of that month.
     */
    private List<LocalDate> generateMonthlyDates(
            Matcher matcher,
            LocalDate calculationDate,
            LocalDate maturityDate
    ) {

        int requestedDay =
                Integer.parseInt(matcher.group(1));

        validateDay(requestedDay);

        List<LocalDate> dates =
                new ArrayList<>();

        YearMonth currentMonth =
                YearMonth.from(calculationDate);

        YearMonth lastMonth =
                YearMonth.from(maturityDate);

        while (!currentMonth.isAfter(lastMonth)) {

            int actualDay =
                    Math.min(
                            requestedDay,
                            currentMonth.lengthOfMonth()
                    );

            LocalDate date =
                    currentMonth.atDay(actualDay);

            if (isValidFutureDate(
                    date,
                    calculationDate,
                    maturityDate
            )) {

                dates.add(date);
            }

            currentMonth =
                    currentMonth.plusMonths(1);
        }

        return dates;
    }

    /*
     * ============================================================
     * HELPERS
     * ============================================================
     */

    private boolean isValidFutureDate(
            LocalDate date,
            LocalDate calculationDate,
            LocalDate maturityDate
    ) {

        return date.isAfter(calculationDate)
                && !date.isAfter(maturityDate);
    }

    /**
     * Creates a date safely.
     *
     * Example:
     *
     * 31-Feb-2027
     *
     * returns null instead of throwing.
     */
    private LocalDate createSafeDate(
            int year,
            int month,
            int day
    ) {

        try {

            return LocalDate.of(
                    year,
                    month,
                    day
            );

        } catch (DateTimeException exception) {

            return null;
        }
    }

    private void validateInput(
            Bond bond,
            LocalDate calculationDate
    ) {

        if (bond == null) {

            throw new IllegalArgumentException(
                    "Bond cannot be null"
            );
        }

        if (calculationDate == null) {

            throw new IllegalArgumentException(
                    "Calculation date cannot be null"
            );
        }

        if (bond.getIpDateDescription() == null
                || bond.getIpDateDescription().isBlank()) {

            throw new IllegalArgumentException(
                    "IP date description is missing"
            );
        }

        if (bond.getMaturityDate() == null) {

            throw new IllegalArgumentException(
                    "Maturity date is required"
            );
        }

        if (bond.getMaturityDate()
                .isBefore(calculationDate)) {

            throw new IllegalArgumentException(
                    "Bond maturity date is before calculation date"
            );
        }
    }

    private String normalizeDescription(
            String description
    ) {

        return description
                .trim()
                .replaceAll("\\s+", " ");
    }

    private void validateMonth(int month) {

        if (month < 1 || month > 12) {

            throw new IllegalArgumentException(
                    "Invalid month in IP date description: "
                            + month
            );
        }
    }

    private void validateDay(int day) {

        if (day < 1 || day > 31) {

            throw new IllegalArgumentException(
                    "Invalid day in IP date description: "
                            + day
            );
        }
    }
}