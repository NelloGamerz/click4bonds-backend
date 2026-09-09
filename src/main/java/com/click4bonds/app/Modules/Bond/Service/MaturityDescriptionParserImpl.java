// package com.click4bonds.app.Modules.Bond.Service;

// import java.math.BigDecimal;
// import java.time.LocalDate;
// import java.time.format.DateTimeFormatter;
// import java.time.format.DateTimeParseException;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Locale;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;

// import org.springframework.stereotype.Service;

// import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
// import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

// @Service
// public class MaturityDescriptionParserImpl implements MaturityDescriptionParser {

//     /*
//      * ------------------------------------------------------------
//      * Normal numeric maturity:
//      *
//      * 26-09-2031
//      * 26/09/2031
//      * ------------------------------------------------------------
//      */
//     private static final Pattern DATE_ONLY = Pattern.compile(
//             "^\\s*(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})\\s*$");

//     /*
//      * ------------------------------------------------------------
//      * Normal text-month maturity:
//      *
//      * 23/Jul/31
//      * 23-Jul-31
//      * 23/Jul/2031
//      * 23-Jul-2031
//      * 23/July/31
//      * ------------------------------------------------------------
//      */
//     private static final Pattern DATE_TEXT_MONTH = Pattern.compile(
//             "^\\s*(\\d{1,2})[-/]([A-Za-z]{3,})[-/](\\d{2}|\\d{4})\\s*$");

//     /*
//      * ------------------------------------------------------------
//      * Perpetual:
//      *
//      * Perp
//      * Perpetual
//      * ------------------------------------------------------------
//      */
//     private static final Pattern PERPETUAL = Pattern.compile(
//             "^\\s*perp(?:etual)?\\s*$",
//             Pattern.CASE_INSENSITIVE);

//     /*
//      * ------------------------------------------------------------
//      * Simple frequency amortization:
//      *
//      * 9/11/2024 to 9/11/2033 (10% each year)
//      *
//      * 01/10/2026 to 01/10/2027 (20% Quarterly)
//      *
//      * Also accepts:
//      *
//      * Quartely
//      * Half-yearly
//      * Semi-annual
//      * Monthly
//      * ------------------------------------------------------------
//      */
//     private static final Pattern FIXED_FREQUENCY = Pattern.compile(
//             "^\\s*"
//                     + "(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})"
//                     + "\\s+to\\s+"
//                     + "(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})"
//                     + "\\s*\\("
//                     + "\\s*(\\d+(?:\\.\\d+)?)%"
//                     + "\\s*"
//                     + "(each\\s+year|yearly|annual|"
//                     + "quarterly|quartely|"
//                     + "half[- ]?yearly|semi[- ]?annual|"
//                     + "monthly)"
//                     + "\\s*\\)"
//                     + "\\s*$",
//             Pattern.CASE_INSENSITIVE);

//     /*
//      * ------------------------------------------------------------
//      * Inline fixed-frequency amortization:
//      *
//      * A single maturity date with a fixed repayment frequency and
//      * percentage. This is a compact form of the range format above
//      * where the second date is omitted because it coincides with the
//      * maturity date.
//      *
//      * Examples:
//      *
//      * 31/12/2030 (20% each year)
//      * 31/12/2030 (20% yearly)
//      * 31/12/2030 (20% annual)
//      * 31/12/2027 (10% quarterly)
//      * 31/12/2027 (5% monthly)
//      * 31/12/2031 (10% half-yearly)
//      *
//      * Supported date separators: '/' and '-'.
//      *
//      * Semantics:
//      * The maturity/end date is the only date supplied. A
//      * FixedFrequencyAmortizationRule is produced whose endDate equals
//      * that date. The startDate is derived so that a full amortization
//      * of the face value ends exactly on the maturity date, i.e. the
//      * rule starts enough periods earlier that
//      *
//      *     (100 / percentage)
//      *
//      * equal installments are produced.
//      * ------------------------------------------------------------
//      */
//     private static final Pattern INLINE_FIXED_FREQUENCY = Pattern.compile(
//             "^\\s*"
//                     + "(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})"
//                     + "\\s*\\("
//                     + "\\s*(\\d+(?:\\.\\d+)?)%"
//                     + "\\s*"
//                     + "(each\\s+year|yearly|annual|"
//                     + "quarterly|quartely|"
//                     + "half[- ]?yearly|semi[- ]?annual|"
//                     + "monthly)"
//                     + "\\s*\\)"
//                     + "\\s*$",
//             Pattern.CASE_INSENSITIVE);

//     /*
//      * ------------------------------------------------------------
//      * IP-based amortization:
//      *
//      * 26-09-2031
//      * (2.5% on Each IP till 2027)
//      *
//      * This parser is deliberately used after the maturity date.
//      * ------------------------------------------------------------
//      */
//     private static final Pattern IP_RULE = Pattern.compile(
//             "(\\d+(?:\\.\\d+)?)%"
//                     + "\\s*on\\s*each\\s*ip"
//                     + "\\s*(?:till|until|upto)"
//                     + "\\s*(\\d{4})",
//             Pattern.CASE_INSENSITIVE);

//     /*
//      * ------------------------------------------------------------
//      * Staged IP amortization:
//      *
//      * 26-09-2031
//      * (2.5% on Each IP till 2027
//      * and 10% on Each IP from 2028 to 2031)
//      * ------------------------------------------------------------
//      */
//     private static final Pattern STAGED_IP_RULE = Pattern.compile(
//             "(\\d+(?:\\.\\d+)?)%"
//                     + "\\s*on\\s*each\\s*ip"
//                     + "\\s*from\\s*(\\d{4})"
//                     + "\\s*(?:to|till|until)\\s*(\\d{4})",
//             Pattern.CASE_INSENSITIVE);

//     @Override
//     public MaturitySchedule parse(
//             String maturityDescription,
//             LocalDate normalizedMaturityDate) {

//         /*
//          * --------------------------------------------------------
//          * NULL / BLANK DESCRIPTION
//          * --------------------------------------------------------
//          *
//          * If no maturity description is supplied, fall back to
//          * the normalized maturity date from the Bond entity.
//          */
//         if (maturityDescription == null
//                 || maturityDescription.isBlank()) {

//             return normalSchedule(normalizedMaturityDate);
//         }

//         String description = normalize(maturityDescription);

//         /*
//          * --------------------------------------------------------
//          * PERPETUAL
//          * --------------------------------------------------------
//          */
//         if (PERPETUAL.matcher(description).matches()) {

//             return new MaturitySchedule(
//                     null,
//                     List.of(),
//                     true);
//         }

//         /*
//          * --------------------------------------------------------
//          * NUMERIC DATE ONLY
//          *
//          * Examples:
//          *
//          * 26/09/2031
//          * 26-09-2031
//          * --------------------------------------------------------
//          */
//         Matcher dateMatcher = DATE_ONLY.matcher(description);

//         if (dateMatcher.matches()) {

//             LocalDate parsedDate = parseDate(
//                     dateMatcher.group(1),
//                     dateMatcher.group(2),
//                     dateMatcher.group(3));

//             return normalSchedule(parsedDate);
//         }

//         /*
//          * --------------------------------------------------------
//          * TEXT-MONTH DATE ONLY
//          *
//          * Examples:
//          *
//          * 23/Jul/31
//          * 23-Jul-31
//          * 23/Jul/2031
//          * 23-Jul-2031
//          * --------------------------------------------------------
//          */
//         Matcher textMonthMatcher = DATE_TEXT_MONTH.matcher(description);

//         if (textMonthMatcher.matches()) {

//             LocalDate parsedDate = parseTextMonthDate(
//                     textMonthMatcher.group(1),
//                     textMonthMatcher.group(2),
//                     textMonthMatcher.group(3));

//             return normalSchedule(parsedDate);
//         }

//         /*
//          * --------------------------------------------------------
//          * FIXED FREQUENCY AMORTIZATION
//          * --------------------------------------------------------
//          *
//          * Example:
//          *
//          * 9/11/2024 to 9/11/2033 (10% each year)
//          *
//          * 01/10/2026 to 01/10/2027 (20% Quarterly)
//          */
//         Matcher fixedMatcher = FIXED_FREQUENCY.matcher(description);

//         if (fixedMatcher.matches()) {

//             LocalDate startDate = parseDate(
//                     fixedMatcher.group(1),
//                     fixedMatcher.group(2),
//                     fixedMatcher.group(3));

//             LocalDate endDate = parseDate(
//                     fixedMatcher.group(4),
//                     fixedMatcher.group(5),
//                     fixedMatcher.group(6));

//             BigDecimal percentage = new BigDecimal(fixedMatcher.group(7));

//             CouponFrequency frequency = parseFrequency(fixedMatcher.group(8));

//             return new MaturitySchedule(
//                     endDate,
//                     List.of(
//                             new FixedFrequencyAmortizationRule(
//                                     percentage,
//                                     startDate,
//                                     endDate,
//                                     frequency)),
//                     false);
//         }

//         /*
//          * --------------------------------------------------------
//          * INLINE FIXED-FREQUENCY AMORTIZATION
//          * --------------------------------------------------------
//          *
//          * A single maturity/end date together with a fixed repayment
//          * frequency and percentage.
//          *
//          * Example:
//          *
//          * 31/12/2030 (20% each year)
//          *
//          * The parsed date is both the maturity date and the rule's end
//          * date. The rule's start date is derived so that the full face
//          * value is amortized by maturity (see deriveInlineStartDate).
//          */
//         Matcher inlineFixedMatcher = INLINE_FIXED_FREQUENCY.matcher(description);

//         if (inlineFixedMatcher.matches()) {

//             LocalDate endDate = parseDate(
//                     inlineFixedMatcher.group(1),
//                     inlineFixedMatcher.group(2),
//                     inlineFixedMatcher.group(3));

//             BigDecimal percentage = new BigDecimal(inlineFixedMatcher.group(4));

//             CouponFrequency frequency = parseFrequency(inlineFixedMatcher.group(5));

//             LocalDate startDate = deriveInlineStartDate(
//                     endDate,
//                     percentage,
//                     frequency);

//             return new MaturitySchedule(
//                     endDate,
//                     List.of(
//                             new FixedFrequencyAmortizationRule(
//                                     percentage,
//                                     startDate,
//                                     endDate,
//                                     frequency)),
//                     false);
//         }

//         /*
//          * --------------------------------------------------------
//          * STAGED / IP-BASED AMORTIZATION
//          * --------------------------------------------------------
//          *
//          * The maturity date is expected at the beginning.
//          *
//          * Examples:
//          *
//          * 26-09-2031 (2.5% on Each IP till 2027)
//          *
//          * 26-09-2031
//          * (2.5% on Each IP till 2027
//          * and 10% on Each IP from 2028 to 2031)
//          *
//          * Also supports:
//          *
//          * 23/Jul/31 (2.5% on Each IP till 2027)
//          */
//         LocalDate maturityDate = extractLeadingDate(description);

//         if (maturityDate != null) {

//             List<AmortizationRule> rules = parseIpRules(
//                     description,
//                     maturityDate);

//             if (!rules.isEmpty()) {

//                 return new MaturitySchedule(
//                         maturityDate,
//                         rules,
//                         false);
//             }
//         }

//         /*
//          * --------------------------------------------------------
//          * UNSUPPORTED DESCRIPTION
//          * --------------------------------------------------------
//          */
//         throw new IllegalArgumentException(
//                 "Unsupported maturity description: "
//                         + maturityDescription);
//     }

//     /*
//      * ============================================================
//      * IP RULE PARSING
//      * ============================================================
//      */
//     private List<AmortizationRule> parseIpRules(
//             String description,
//             LocalDate maturityDate) {

//         List<AmortizationRule> rules = new ArrayList<>();

//         /*
//          * --------------------------------------------------------
//          * Example:
//          *
//          * 2.5% on Each IP till 2027
//          * --------------------------------------------------------
//          */
//         Matcher untilMatcher = IP_RULE.matcher(description);

//         while (untilMatcher.find()) {

//             BigDecimal percentage = new BigDecimal(
//                     untilMatcher.group(1));

//             int endYear = Integer.parseInt(
//                     untilMatcher.group(2));

//             LocalDate startDate = LocalDate.of(
//                     maturityDate.getYear() - 7,
//                     1,
//                     1);

//             LocalDate endDate = LocalDate.of(
//                     endYear,
//                     12,
//                     31);

//             rules.add(
//                     new IpBasedAmortizationRule(
//                             percentage,
//                             startDate,
//                             endDate));
//         }

//         /*
//          * --------------------------------------------------------
//          * Example:
//          *
//          * 10% on Each IP from 2028 to 2031
//          * --------------------------------------------------------
//          */
//         Matcher stagedMatcher = STAGED_IP_RULE.matcher(description);

//         while (stagedMatcher.find()) {

//             BigDecimal percentage = new BigDecimal(
//                     stagedMatcher.group(1));

//             int startYear = Integer.parseInt(
//                     stagedMatcher.group(2));

//             int endYear = Integer.parseInt(
//                     stagedMatcher.group(3));

//             LocalDate startDate = LocalDate.of(
//                     startYear,
//                     1,
//                     1);

//             LocalDate endDate = LocalDate.of(
//                     endYear,
//                     12,
//                     31);

//             rules.add(
//                     new IpBasedAmortizationRule(
//                             percentage,
//                             startDate,
//                             endDate));
//         }

//         return rules;
//     }

//     /*
//      * ============================================================
//      * HELPERS
//      * ============================================================
//      */

//     /*
//      * ------------------------------------------------------------
//      * Normal maturity schedule
//      * ------------------------------------------------------------
//      */
//     private MaturitySchedule normalSchedule(
//             LocalDate maturityDate) {

//         if (maturityDate == null) {

//             throw new IllegalArgumentException(
//                     "Maturity date is required");
//         }

//         return new MaturitySchedule(
//                 maturityDate,
//                 List.of(),
//                 false);
//     }

//     /*
//      * ------------------------------------------------------------
//      * Extract leading maturity date
//      *
//      * Supports:
//      *
//      * 26-09-2031
//      * 26/09/2031
//      * 23/Jul/31
//      * 23-Jul-31
//      * 23/Jul/2031
//      * ------------------------------------------------------------
//      */
//     private LocalDate extractLeadingDate(
//             String description) {

//         /*
//          * First try numeric date.
//          */
//         Matcher numericMatcher = Pattern.compile(
//                 "^\\s*"
//                         + "(\\d{1,2})[-/]"
//                         + "(\\d{1,2})[-/]"
//                         + "(\\d{4})")
//                 .matcher(description);

//         if (numericMatcher.find()) {

//             return parseDate(
//                     numericMatcher.group(1),
//                     numericMatcher.group(2),
//                     numericMatcher.group(3));
//         }

//         /*
//          * Then try text-month date.
//          */
//         Matcher textMonthMatcher = Pattern.compile(
//                 "^\\s*"
//                         + "(\\d{1,2})[-/]"
//                         + "([A-Za-z]{3,})[-/]"
//                         + "(\\d{2}|\\d{4})")
//                 .matcher(description);

//         if (textMonthMatcher.find()) {

//             return parseTextMonthDate(
//                     textMonthMatcher.group(1),
//                     textMonthMatcher.group(2),
//                     textMonthMatcher.group(3));
//         }

//         return null;
//     }

//     /*
//      * ------------------------------------------------------------
//      * Parse numeric date
//      *
//      * Example:
//      *
//      * 23/07/2031
//      * ------------------------------------------------------------
//      */
//     private LocalDate parseDate(
//             String day,
//             String month,
//             String year) {

//         try {

//             return LocalDate.of(
//                     Integer.parseInt(year),
//                     Integer.parseInt(month),
//                     Integer.parseInt(day));

//         } catch (RuntimeException e) {

//             throw new IllegalArgumentException(
//                     "Invalid maturity date: "
//                             + day + "/" + month + "/" + year,
//                     e);
//         }
//     }

//     /*
//      * ------------------------------------------------------------
//      * Parse text-month date
//      *
//      * Supports:
//      *
//      * 23/Jul/31
//      * 23-Jul-31
//      * 23/Jul/2031
//      * 23-Jul-2031
//      *
//      * Two-digit years are interpreted as 20xx.
//      * ------------------------------------------------------------
//      */
//     private LocalDate parseTextMonthDate(
//             String day,
//             String month,
//             String year) {

//         String normalizedYear = year;

//         /*
//          * Convert:
//          *
//          * 31 -> 2031
//          *
//          * 28 -> 2028
//          */
//         if (year.length() == 2) {

//             normalizedYear = "20" + year;
//         }

//         String dateValue = day
//                 + "/"
//                 + month
//                 + "/"
//                 + normalizedYear;

//         /*
//          * Use MMM first. This handles:
//          *
//          * Jan
//          * Feb
//          * Mar
//          * Apr
//          * May
//          * Jun
//          * Jul
//          * Aug
//          * Sep
//          * Oct
//          * Nov
//          * Dec
//          *
//          * Locale.ENGLISH ensures that month names are parsed
//          * independently of the server/JVM locale.
//          */
//         DateTimeFormatter shortMonthFormatter = DateTimeFormatter.ofPattern(
//                 "d/MMM/uuuu",
//                 Locale.ENGLISH);

//         try {

//             return LocalDate.parse(
//                     dateValue,
//                     shortMonthFormatter);

//         } catch (DateTimeParseException shortMonthException) {

//             /*
//              * Also support full month names such as:
//              *
//              * 23/July/2031
//              */
//             DateTimeFormatter fullMonthFormatter = DateTimeFormatter.ofPattern(
//                     "d/MMMM/uuuu",
//                     Locale.ENGLISH);

//             try {

//                 return LocalDate.parse(
//                         dateValue,
//                         fullMonthFormatter);

//             } catch (DateTimeParseException fullMonthException) {

//                 throw new IllegalArgumentException(
//                         "Invalid maturity date: "
//                                 + dateValue,
//                         fullMonthException);
//             }
//         }
//     }

//     /*
//      * ------------------------------------------------------------
//      * Parse amortization frequency
//      * ------------------------------------------------------------
//      */
//     private CouponFrequency parseFrequency(
//             String value) {

//         String normalized = value
//                 .trim()
//                 .toLowerCase()
//                 .replaceAll("\\s+", " ");

//         return switch (normalized) {

//             case "each year",
//                     "yearly",
//                     "annual" ->
//                 CouponFrequency.YEARLY;

//             case "quarterly",
//                     "quartely" ->
//                 CouponFrequency.QUARTERLY;

//             case "half-yearly",
//                     "half yearly",
//                     "semi-annual" ->
//                 CouponFrequency.HALF_YEARLY;

//             case "monthly" ->
//                 CouponFrequency.MONTHLY;

//             default ->
//                 throw new IllegalArgumentException(
//                         "Unsupported amortization frequency: "
//                                 + value);
//         };
//     }

//     /*
//      * ------------------------------------------------------------
//      * Derive the inline-format start date
//      *
//      * For an inline fixed-frequency rule only the maturity/end date is
//      * supplied. This computes the start date by walking backwards from
//      * the end date so that exactly
//      *
//      *     (100 / percentage)
//      *
//      * equal installments are produced and the full face value is
//      * amortized on the maturity date.
//      *
//      * Example:
//      *
//      * 31/12/2030 (20% each year)
//      *   -> 5 yearly installments ending 31/12/2030
//      *   -> start date 31/12/2026
//      * ------------------------------------------------------------
//      */
//     private LocalDate deriveInlineStartDate(
//             LocalDate endDate,
//             BigDecimal percentage,
//             CouponFrequency frequency) {

//         if (percentage == null
//                 || percentage.signum() <= 0) {

//             throw new IllegalArgumentException(
//                     "Amortization percentage must be positive: "
//                             + percentage);
//         }

//         int installmentCount = (int) Math.ceil(
//                 100.0 / percentage.doubleValue());

//         if (installmentCount < 1) {

//             installmentCount = 1;
//         }

//         return subtractPeriods(
//                 endDate,
//                 installmentCount - 1,
//                 frequency);
//     }

//     /*
//      * ------------------------------------------------------------
//      * Walk a date backwards by a whole number of coupon periods
//      * ------------------------------------------------------------
//      */
//     private LocalDate subtractPeriods(
//             LocalDate date,
//             int periods,
//             CouponFrequency frequency) {

//         if (periods <= 0) {

//             return date;
//         }

//         return switch (frequency) {

//             case MONTHLY ->
//                 date.minusMonths(periods);

//             case QUARTERLY ->
//                 date.minusMonths((long) periods * 3);

//             case HALF_YEARLY ->
//                 date.minusMonths((long) periods * 6);

//             case YEARLY,
//                     AT_MATURITY ->
//                 date.minusYears(periods);
//         };
//     }

//     /*
//      * ------------------------------------------------------------
//      * Normalize description
//      *
//      * Keeps the original date separators while removing
//      * unnecessary whitespace.
//      * ------------------------------------------------------------
//      */
//     private String normalize(
//             String value) {

//         return value
//                 .trim()
//                 .replaceAll("\\s+", " ");
//     }
// }

package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

@Service
public class MaturityDescriptionParserImpl
                implements MaturityDescriptionParser {

        /*
         * ============================================================
         * DATE PATTERNS
         * ============================================================
         */

        /**
         * Numeric date:
         *
         * 26-09-2031
         * 26/09/2031
         */
        private static final Pattern DATE_ONLY = Pattern.compile(
                        "^\\s*(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})\\s*$");

        /**
         * Text month date:
         *
         * 23/Jul/31
         * 23-Jul-31
         * 23/Jul/2031
         * 23-July-2031
         */
        private static final Pattern DATE_TEXT_MONTH = Pattern.compile(
                        "^\\s*(\\d{1,2})[-/]([A-Za-z]{3,})[-/](\\d{2}|\\d{4})\\s*$");

        /**
         * Leading numeric date.
         */
        private static final Pattern LEADING_NUMERIC_DATE = Pattern.compile(
                        "^\\s*(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})");

        /**
         * Leading text-month date.
         */
        private static final Pattern LEADING_TEXT_MONTH_DATE = Pattern.compile(
                        "^\\s*(\\d{1,2})[-/]([A-Za-z]{3,})[-/](\\d{2}|\\d{4})");

        /*
         * ============================================================
         * PERPETUAL
         * ============================================================
         */

        private static final Pattern PERPETUAL = Pattern.compile(
                        "^\\s*perp(?:etual)?\\s*$",
                        Pattern.CASE_INSENSITIVE);

        /*
         * ============================================================
         * FIXED FREQUENCY AMORTIZATION
         *
         * Explicit range:
         *
         * 01/10/2026 to 01/10/2027 (20% Quarterly)
         *
         * 9/11/2024 to 9/11/2033 (10% each year)
         * ============================================================
         */

        private static final Pattern FIXED_FREQUENCY = Pattern.compile(
                        "^\\s*"
                                        + "(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})"
                                        + "\\s+to\\s+"
                                        + "(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})"
                                        + "\\s*\\("
                                        + "\\s*(\\d+(?:\\.\\d+)?)%"
                                        + "\\s*"
                                        + "(each\\s+year|yearly|annual|"
                                        + "quarterly|quartely|"
                                        + "half[- ]?yearly|semi[- ]?annual|"
                                        + "monthly)"
                                        + "\\s*\\)"
                                        + "\\s*$",
                        Pattern.CASE_INSENSITIVE);

        /*
         * ============================================================
         * INLINE FIXED FREQUENCY
         *
         * 31/12/2030 (20% each year)
         * 31/12/2027 (10% quarterly)
         * 31/12/2027 (5% monthly)
         * 31/12/2031 (10% half-yearly)
         * ============================================================
         */

        private static final Pattern INLINE_FIXED_FREQUENCY = Pattern.compile(
                        "^\\s*"
                                        + "(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})"
                                        + "\\s*\\("
                                        + "\\s*(\\d+(?:\\.\\d+)?)%"
                                        + "\\s*"
                                        + "(each\\s+year|yearly|annual|"
                                        + "quarterly|quartely|"
                                        + "half[- ]?yearly|semi[- ]?annual|"
                                        + "monthly)"
                                        + "\\s*\\)"
                                        + "\\s*$",
                        Pattern.CASE_INSENSITIVE);

        /*
         * ============================================================
         * IP BASED AMORTIZATION
         *
         * 26-09-2031 (2.5% on Each IP till 2027)
         * ============================================================
         */

        private static final Pattern IP_RULE = Pattern.compile(
                        "(\\d+(?:\\.\\d+)?)%"
                                        + "\\s*on\\s*each\\s*ip"
                                        + "\\s*(?:till|until|upto)"
                                        + "\\s*(\\d{4})",
                        Pattern.CASE_INSENSITIVE);

        /*
         * ============================================================
         * STAGED IP AMORTIZATION
         *
         * 10% on Each IP from 2028 to 2031
         * ============================================================
         */

        private static final Pattern STAGED_IP_RULE = Pattern.compile(
                        "(\\d+(?:\\.\\d+)?)%"
                                        + "\\s*on\\s*each\\s*ip"
                                        + "\\s*from\\s*(\\d{4})"
                                        + "\\s*(?:to|till|until)"
                                        + "\\s*(\\d{4})",
                        Pattern.CASE_INSENSITIVE);

        /*
         * ============================================================
         * PUBLIC PARSER
         * ============================================================
         */

        @Override
        public MaturitySchedule parse(
                        String maturityDescription,
                        LocalDate normalizedMaturityDate) {

                /*
                 * --------------------------------------------------------
                 * NULL / BLANK
                 * --------------------------------------------------------
                 */

                if (maturityDescription == null
                                || maturityDescription.isBlank()) {

                        return normalSchedule(normalizedMaturityDate);
                }

                String description = normalize(maturityDescription);

                /*
                 * --------------------------------------------------------
                 * PERPETUAL
                 * --------------------------------------------------------
                 */

                if (PERPETUAL.matcher(description).matches()) {

                        return new MaturitySchedule(
                                        null,
                                        List.of(),
                                        true);
                }

                /*
                 * --------------------------------------------------------
                 * NORMAL NUMERIC DATE
                 * --------------------------------------------------------
                 */

                Matcher dateMatcher = DATE_ONLY.matcher(description);

                if (dateMatcher.matches()) {

                        LocalDate maturityDate = parseDate(
                                        dateMatcher.group(1),
                                        dateMatcher.group(2),
                                        dateMatcher.group(3));

                        return normalSchedule(maturityDate);
                }

                /*
                 * --------------------------------------------------------
                 * NORMAL TEXT-MONTH DATE
                 * --------------------------------------------------------
                 */

                Matcher textMonthMatcher = DATE_TEXT_MONTH.matcher(description);

                if (textMonthMatcher.matches()) {

                        LocalDate maturityDate = parseTextMonthDate(
                                        textMonthMatcher.group(1),
                                        textMonthMatcher.group(2),
                                        textMonthMatcher.group(3));

                        return normalSchedule(maturityDate);
                }

                /*
                 * --------------------------------------------------------
                 * EXPLICIT FIXED FREQUENCY AMORTIZATION
                 *
                 * IMPORTANT:
                 *
                 * The explicit start/end dates are authoritative.
                 *
                 * For:
                 *
                 * 01/10/2026 to 01/10/2027 (20% Quartely)
                 *
                 * we MUST preserve:
                 *
                 * start = 2026-10-01
                 * end = 2027-10-01
                 * freq = QUARTERLY
                 *
                 * Do NOT derive the frequency from the coupon frequency.
                 * --------------------------------------------------------
                 */

                Matcher fixedMatcher = FIXED_FREQUENCY.matcher(description);

                if (fixedMatcher.matches()) {

                        LocalDate startDate = parseDate(
                                        fixedMatcher.group(1),
                                        fixedMatcher.group(2),
                                        fixedMatcher.group(3));

                        LocalDate endDate = parseDate(
                                        fixedMatcher.group(4),
                                        fixedMatcher.group(5),
                                        fixedMatcher.group(6));

                        BigDecimal percentage = parsePercentage(fixedMatcher.group(7));

                        CouponFrequency frequency = parseFrequency(fixedMatcher.group(8));

                        validateFixedFrequencyRule(
                                        startDate,
                                        endDate,
                                        percentage,
                                        frequency);

                        return new MaturitySchedule(
                                        endDate,
                                        List.of(
                                                        new FixedFrequencyAmortizationRule(
                                                                        percentage,
                                                                        startDate,
                                                                        endDate,
                                                                        frequency)),
                                        false);
                }

                /*
                 * --------------------------------------------------------
                 * INLINE FIXED FREQUENCY AMORTIZATION
                 *
                 * Example:
                 *
                 * 31/12/2030 (20% each year)
                 *
                 * Since there is no explicit start date, calculate it
                 * backwards from maturity.
                 * --------------------------------------------------------
                 */

                Matcher inlineFixedMatcher = INLINE_FIXED_FREQUENCY.matcher(description);

                if (inlineFixedMatcher.matches()) {

                        LocalDate endDate = parseDate(
                                        inlineFixedMatcher.group(1),
                                        inlineFixedMatcher.group(2),
                                        inlineFixedMatcher.group(3));

                        BigDecimal percentage = parsePercentage(inlineFixedMatcher.group(4));

                        CouponFrequency frequency = parseFrequency(inlineFixedMatcher.group(5));

                        LocalDate startDate = deriveInlineStartDate(
                                        endDate,
                                        percentage,
                                        frequency);

                        validateFixedFrequencyRule(
                                        startDate,
                                        endDate,
                                        percentage,
                                        frequency);

                        return new MaturitySchedule(
                                        endDate,
                                        List.of(
                                                        new FixedFrequencyAmortizationRule(
                                                                        percentage,
                                                                        startDate,
                                                                        endDate,
                                                                        frequency)),
                                        false);
                }

                /*
                 * --------------------------------------------------------
                 * IP / STAGED AMORTIZATION
                 * --------------------------------------------------------
                 */

                LocalDate maturityDate = extractLeadingDate(description);

                if (maturityDate != null) {

                        List<AmortizationRule> rules = parseIpRules(
                                        description,
                                        maturityDate);

                        if (!rules.isEmpty()) {

                                return new MaturitySchedule(
                                                maturityDate,
                                                rules,
                                                false);
                        }
                }

                /*
                 * --------------------------------------------------------
                 * UNSUPPORTED
                 * --------------------------------------------------------
                 */

                throw new IllegalArgumentException(
                                "Unsupported maturity description: "
                                                + maturityDescription);
        }

        /*
         * ============================================================
         * IP RULE PARSING
         * ============================================================
         */

        private List<AmortizationRule> parseIpRules(
                        String description,
                        LocalDate maturityDate) {

                List<AmortizationRule> rules = new ArrayList<>();

                /*
                 * --------------------------------------------------------
                 * "2.5% on Each IP till 2027"
                 * --------------------------------------------------------
                 */

                Matcher untilMatcher = IP_RULE.matcher(description);

                while (untilMatcher.find()) {

                        BigDecimal percentage = parsePercentage(
                                        untilMatcher.group(1));

                        int endYear = Integer.parseInt(
                                        untilMatcher.group(2));

                        /*
                         * Preserve existing business rule:
                         *
                         * maturity year - 7
                         *
                         * However, don't allow the calculated start to become
                         * later than the end date.
                         */
                        int startYear = maturityDate.getYear() - 7;

                        LocalDate startDate = LocalDate.of(
                                        startYear,
                                        1,
                                        1);

                        LocalDate endDate = LocalDate.of(
                                        endYear,
                                        12,
                                        31);

                        validateIpRule(
                                        percentage,
                                        startDate,
                                        endDate);

                        rules.add(
                                        new IpBasedAmortizationRule(
                                                        percentage,
                                                        startDate,
                                                        endDate));
                }

                /*
                 * --------------------------------------------------------
                 * "10% on Each IP from 2028 to 2031"
                 * --------------------------------------------------------
                 */

                Matcher stagedMatcher = STAGED_IP_RULE.matcher(description);

                while (stagedMatcher.find()) {

                        BigDecimal percentage = parsePercentage(
                                        stagedMatcher.group(1));

                        int startYear = Integer.parseInt(
                                        stagedMatcher.group(2));

                        int endYear = Integer.parseInt(
                                        stagedMatcher.group(3));

                        LocalDate startDate = LocalDate.of(
                                        startYear,
                                        1,
                                        1);

                        LocalDate endDate = LocalDate.of(
                                        endYear,
                                        12,
                                        31);

                        validateIpRule(
                                        percentage,
                                        startDate,
                                        endDate);

                        rules.add(
                                        new IpBasedAmortizationRule(
                                                        percentage,
                                                        startDate,
                                                        endDate));
                }

                /*
                 * --------------------------------------------------------
                 * Sort rules chronologically.
                 *
                 * This is important when the description contains multiple
                 * staged rules.
                 * --------------------------------------------------------
                 */

                rules.sort(
                                Comparator.comparing(
                                                AmortizationRule::startDate));

                return rules;
        }

        /*
         * ============================================================
         * INLINE START DATE CALCULATION
         * ============================================================
         *
         * Example:
         *
         * 31/12/2030 (20% each year)
         *
         * 100 / 20 = 5 installments
         *
         * Dates:
         *
         * 31/12/2026
         * 31/12/2027
         * 31/12/2028
         * 31/12/2029
         * 31/12/2030
         *
         * Therefore:
         *
         * startDate = 31/12/2026
         *
         * IMPORTANT:
         *
         * We use BigDecimal instead of double.
         * ============================================================
         */

        private LocalDate deriveInlineStartDate(
                        LocalDate endDate,
                        BigDecimal percentage,
                        CouponFrequency frequency) {

                validatePercentage(percentage);

                /*
                 * Full amortization requires:
                 *
                 * 100 / percentage
                 *
                 * installments.
                 *
                 * Example:
                 *
                 * 100 / 20 = 5
                 * 100 / 10 = 10
                 * 100 / 5 = 20
                 */
                BigDecimal installmentCountDecimal = BigDecimal.valueOf(100)
                                .divide(
                                                percentage,
                                                10,
                                                RoundingMode.HALF_UP);

                /*
                 * The percentage must divide 100 exactly.
                 *
                 * Otherwise the parser cannot construct a perfectly
                 * amortizing fixed installment schedule.
                 *
                 * Example:
                 *
                 * 7% => 14.2857 installments
                 *
                 * This is not a complete fixed-percentage schedule.
                 */
                if (installmentCountDecimal.stripTrailingZeros()
                                .scale() > 0) {

                        throw new IllegalArgumentException(
                                        "Amortization percentage must divide 100 exactly: "
                                                        + percentage);
                }

                int installmentCount = installmentCountDecimal.intValueExact();

                if (installmentCount <= 0) {

                        throw new IllegalArgumentException(
                                        "Invalid amortization installment count: "
                                                        + installmentCount);
                }

                /*
                 * If there are N payments, there are N - 1 periods between
                 * the first and final payment dates.
                 */
                int periods = installmentCount - 1;

                return subtractPeriods(
                                endDate,
                                periods,
                                frequency);
        }

        /*
         * ============================================================
         * DATE PERIOD CALCULATION
         * ============================================================
         */

        private LocalDate subtractPeriods(
                        LocalDate date,
                        int periods,
                        CouponFrequency frequency) {

                if (periods <= 0) {
                        return date;
                }

                return switch (frequency) {

                        case MONTHLY ->
                                date.minusMonths(periods);

                        case QUARTERLY ->
                                date.minusMonths(
                                                (long) periods * 3);

                        case HALF_YEARLY ->
                                date.minusMonths(
                                                (long) periods * 6);

                        case YEARLY ->
                                date.minusYears(periods);

                        case AT_MATURITY ->
                                date;
                };
        }

        /*
         * ============================================================
         * VALIDATION
         * ============================================================
         */

        private void validateFixedFrequencyRule(
                        LocalDate startDate,
                        LocalDate endDate,
                        BigDecimal percentage,
                        CouponFrequency frequency) {

                if (startDate == null) {

                        throw new IllegalArgumentException(
                                        "Amortization start date is required");
                }

                if (endDate == null) {

                        throw new IllegalArgumentException(
                                        "Amortization end date is required");
                }

                if (startDate.isAfter(endDate)) {

                        throw new IllegalArgumentException(
                                        "Amortization start date cannot be after "
                                                        + "end date: "
                                                        + startDate
                                                        + " > "
                                                        + endDate);
                }

                validatePercentage(percentage);

                if (frequency == null) {

                        throw new IllegalArgumentException(
                                        "Amortization frequency is required");
                }
        }

        private void validateIpRule(
                        BigDecimal percentage,
                        LocalDate startDate,
                        LocalDate endDate) {

                validatePercentage(percentage);

                if (startDate.isAfter(endDate)) {

                        throw new IllegalArgumentException(
                                        "IP amortization start date cannot be after "
                                                        + "end date: "
                                                        + startDate
                                                        + " > "
                                                        + endDate);
                }
        }

        private void validatePercentage(
                        BigDecimal percentage) {

                if (percentage == null) {

                        throw new IllegalArgumentException(
                                        "Amortization percentage is required");
                }

                if (percentage.signum() <= 0) {

                        throw new IllegalArgumentException(
                                        "Amortization percentage must be greater than zero: "
                                                        + percentage);
                }

                if (percentage.compareTo(
                                BigDecimal.valueOf(100)) > 0) {

                        throw new IllegalArgumentException(
                                        "Amortization percentage cannot exceed 100: "
                                                        + percentage);
                }
        }

        /*
         * ============================================================
         * NORMAL SCHEDULE
         * ============================================================
         */

        private MaturitySchedule normalSchedule(
                        LocalDate maturityDate) {

                if (maturityDate == null) {

                        throw new IllegalArgumentException(
                                        "Maturity date is required");
                }

                return new MaturitySchedule(
                                maturityDate,
                                List.of(),
                                false);
        }

        /*
         * ============================================================
         * EXTRACT LEADING DATE
         * ============================================================
         */

        private LocalDate extractLeadingDate(
                        String description) {

                /*
                 * Numeric:
                 *
                 * 26-09-2031 ...
                 * 26/09/2031 ...
                 */
                Matcher numericMatcher = LEADING_NUMERIC_DATE.matcher(description);

                if (numericMatcher.find()) {

                        return parseDate(
                                        numericMatcher.group(1),
                                        numericMatcher.group(2),
                                        numericMatcher.group(3));
                }

                /*
                 * Text month:
                 *
                 * 23/Jul/31 ...
                 * 23-Jul-2031 ...
                 */
                Matcher textMatcher = LEADING_TEXT_MONTH_DATE.matcher(description);

                if (textMatcher.find()) {

                        return parseTextMonthDate(
                                        textMatcher.group(1),
                                        textMatcher.group(2),
                                        textMatcher.group(3));
                }

                return null;
        }

        /*
         * ============================================================
         * NUMERIC DATE PARSER
         * ============================================================
         */

        private LocalDate parseDate(
                        String day,
                        String month,
                        String year) {

                try {

                        int dayValue = Integer.parseInt(day);

                        int monthValue = Integer.parseInt(month);

                        int yearValue = Integer.parseInt(year);

                        return LocalDate.of(
                                        yearValue,
                                        monthValue,
                                        dayValue);

                } catch (RuntimeException e) {

                        throw new IllegalArgumentException(
                                        "Invalid maturity date: "
                                                        + day
                                                        + "/"
                                                        + month
                                                        + "/"
                                                        + year,
                                        e);
                }
        }

        /*
         * ============================================================
         * TEXT MONTH DATE PARSER
         * ============================================================
         */

        private LocalDate parseTextMonthDate(
                        String day,
                        String month,
                        String year) {

                String normalizedYear = year;

                /*
                 * 31 -> 2031
                 */
                if (year.length() == 2) {

                        normalizedYear = "20" + year;
                }

                String dateValue = day
                                + "/"
                                + month
                                + "/"
                                + normalizedYear;

                /*
                 * First try abbreviated month:
                 *
                 * Jan
                 * Feb
                 * Mar
                 * ...
                 */
                DateTimeFormatter shortMonthFormatter = DateTimeFormatter.ofPattern(
                                "d/MMM/uuuu",
                                Locale.ENGLISH);

                try {

                        return LocalDate.parse(
                                        dateValue,
                                        shortMonthFormatter);

                } catch (DateTimeParseException ignored) {
                        /*
                         * Try full month below.
                         */
                }

                /*
                 * Full month:
                 *
                 * January
                 * February
                 * ...
                 */
                DateTimeFormatter fullMonthFormatter = DateTimeFormatter.ofPattern(
                                "d/MMMM/uuuu",
                                Locale.ENGLISH);

                try {

                        return LocalDate.parse(
                                        dateValue,
                                        fullMonthFormatter);

                } catch (DateTimeParseException e) {

                        throw new IllegalArgumentException(
                                        "Invalid maturity date: "
                                                        + dateValue,
                                        e);
                }
        }

        /*
         * ============================================================
         * PERCENTAGE PARSER
         * ============================================================
         */

        private BigDecimal parsePercentage(
                        String value) {

                try {

                        BigDecimal percentage = new BigDecimal(value.trim());

                        validatePercentage(percentage);

                        return percentage;

                } catch (NumberFormatException e) {

                        throw new IllegalArgumentException(
                                        "Invalid amortization percentage: "
                                                        + value,
                                        e);
                }
        }

        /*
         * ============================================================
         * FREQUENCY PARSER
         * ============================================================
         */

        private CouponFrequency parseFrequency(
                        String value) {

                String normalized = value
                                .trim()
                                .toLowerCase(Locale.ENGLISH)
                                .replaceAll("\\s+", " ");

                return switch (normalized) {

                        case "each year",
                                        "yearly",
                                        "annual" ->
                                CouponFrequency.YEARLY;

                        /*
                         * Both spellings are intentionally supported:
                         *
                         * quarterly
                         * quartely
                         */
                        case "quarterly",
                                        "quartely" ->
                                CouponFrequency.QUARTERLY;

                        case "half-yearly",
                                        "half yearly",
                                        "semi-annual" ->
                                CouponFrequency.HALF_YEARLY;

                        case "monthly" ->
                                CouponFrequency.MONTHLY;

                        default ->
                                throw new IllegalArgumentException(
                                                "Unsupported amortization frequency: "
                                                                + value);
                };
        }

        /*
         * ============================================================
         * STRING NORMALIZATION
         * ============================================================
         */

        private String normalize(
                        String value) {

                return value
                                .trim()
                                .replaceAll("\\s+", " ");
        }
}
