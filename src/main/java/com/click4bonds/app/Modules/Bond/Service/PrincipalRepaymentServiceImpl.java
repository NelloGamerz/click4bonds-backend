package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Models.Bond;

@Service
public class PrincipalRepaymentServiceImpl
        implements PrincipalRepaymentService {

    private static final BigDecimal FACE_VALUE = BigDecimal.valueOf(100);

    /*
     * ------------------------------------------------------------
     * Pattern:
     *
     * 9/11/2024 to 9/11/2033 (10% each year)
     *
     * Groups:
     *
     * 1 = start date
     * 2 = end date
     * 3 = percentage
     * ------------------------------------------------------------
     */
    private static final Pattern SIMPLE_AMORTIZATION = Pattern.compile(
            "^\\s*"
                    + "(\\d{1,2}/\\d{1,2}/\\d{4})"
                    + "\\s+to\\s+"
                    + "(\\d{1,2}/\\d{1,2}/\\d{4})"
                    + "\\s*\\("
                    + "\\s*(\\d+(?:\\.\\d+)?)%"
                    + "\\s*each\\s+year\\s*\\)"
                    + "\\s*$",
            Pattern.CASE_INSENSITIVE);

    /*
     * ------------------------------------------------------------
     * Pattern:
     *
     * 26-09-2031
     *
     * This is a normal maturity bond.
     * ------------------------------------------------------------
     */
    private static final Pattern SIMPLE_MATURITY = Pattern.compile(
            "^\\s*"
                    + "\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}"
                    + "\\s*$");

    @Override
    public List<PrincipalRepayment> generateRepayments(
            Bond bond,
            List<LocalDate> couponDates,
            LocalDate calculationDate) {

        validateInput(
                bond,
                couponDates,
                calculationDate);

        String description = normalize(
                bond.getMaturityDescription());

        /*
         * --------------------------------------------------------
         * No amortization information.
         *
         * Treat as normal bond:
         *
         * ₹100 returned at maturity.
         * --------------------------------------------------------
         */
        if (description == null
                || description.isBlank()) {

            return generateNormalMaturityRepayment(
                    bond,
                    couponDates);
        }

        /*
         * --------------------------------------------------------
         * Simple amortization
         *
         * Example:
         *
         * 9/11/2024 to 9/11/2033 (10% each year)
         * --------------------------------------------------------
         */
        Matcher matcher = SIMPLE_AMORTIZATION.matcher(description);

        if (matcher.matches()) {

            return generateSimpleAmortization(
                    bond,
                    couponDates,
                    matcher);
        }

        /*
         * --------------------------------------------------------
         * Normal maturity date
         * --------------------------------------------------------
         */
        if (SIMPLE_MATURITY.matcher(description).matches()) {

            return generateNormalMaturityRepayment(
                    bond,
                    couponDates);
        }

        /*
         * We intentionally don't guess.
         */
        throw new IllegalArgumentException(
                "Unsupported maturity description: "
                        + description);
    }

    /*
     * ============================================================
     * NORMAL BOND
     * ============================================================
     */

    private List<PrincipalRepayment> generateNormalMaturityRepayment(
            Bond bond,
            List<LocalDate> couponDates) {

        LocalDate maturityDate = bond.getMaturityDate();

        List<PrincipalRepayment> result = new ArrayList<>();

        /*
         * Only add maturity if it is present
         * in the generated coupon schedule.
         */
        if (couponDates.contains(maturityDate)) {

            result.add(
                    new PrincipalRepayment(
                            maturityDate,
                            FACE_VALUE,
                            BigDecimal.ZERO));
        }

        return result;
    }

    /*
     * ============================================================
     * SIMPLE AMORTIZATION
     * ============================================================
     */

    private List<PrincipalRepayment> generateSimpleAmortization(
            Bond bond,
            List<LocalDate> couponDates,
            Matcher matcher) {

        LocalDate startDate = parseDate(matcher.group(1));

        LocalDate endDate = parseDate(matcher.group(2));

        BigDecimal percentage = new BigDecimal(matcher.group(3));

        validatePercentage(percentage);

        List<PrincipalRepayment> result = new ArrayList<>();

        BigDecimal remainingPrincipal = FACE_VALUE;

        /*
         * Find coupon dates between
         * amortization start and end.
         */
        for (LocalDate couponDate : couponDates) {

            if (couponDate.isBefore(startDate)) {
                continue;
            }

            if (couponDate.isAfter(endDate)) {
                continue;
            }

            /*
             * Principal repayment:
             *
             * 100 × 10% = 10
             */
            BigDecimal repayment = FACE_VALUE
                    .multiply(percentage)
                    .divide(
                            BigDecimal.valueOf(100),
                            10,
                            RoundingMode.HALF_UP);

            /*
             * Never repay more than
             * the remaining principal.
             */
            repayment = repayment.min(
                    remainingPrincipal);

            remainingPrincipal = remainingPrincipal.subtract(
                    repayment);

            result.add(
                    new PrincipalRepayment(
                            couponDate,
                            repayment,
                            remainingPrincipal));

            /*
             * Principal is fully repaid.
             */
            if (remainingPrincipal
                    .compareTo(BigDecimal.ZERO) == 0) {

                break;
            }
        }

        /*
         * Safety:
         *
         * If the amortization percentages do not
         * completely repay ₹100, the final maturity
         * date should repay the remainder.
         */
        if (remainingPrincipal
                .compareTo(BigDecimal.ZERO) > 0) {

            LocalDate maturityDate = bond.getMaturityDate();

            boolean maturityAlreadyExists = result.stream()
                    .anyMatch(
                            repayment -> repayment.date()
                                    .equals(maturityDate));

            if (maturityAlreadyExists) {

                /*
                 * Add remaining principal to the
                 * existing maturity repayment.
                 */
                // result = addRemainingToMaturity(
                // result,
                // remainingPrincipal);

                result = addRemainingToMaturity(
                        result,
                        maturityDate,
                        remainingPrincipal);

            } else if (couponDates.contains(
                    maturityDate)) {

                result.add(
                        new PrincipalRepayment(
                                maturityDate,
                                remainingPrincipal,
                                BigDecimal.ZERO));
            }
        }

        return result.stream()
                .sorted(
                        Comparator.comparing(
                                PrincipalRepayment::date))
                .toList();
    }

    /*
     * ============================================================
     * HELPERS
     * ============================================================
     */

    // private List<PrincipalRepayment> addRemainingToMaturity(
    // List<PrincipalRepayment> repayments,
    // BigDecimal remaining
    // ) {

    // List<PrincipalRepayment> result =
    // new ArrayList<>();

    // for (PrincipalRepayment repayment :
    // repayments) {

    // if (repayment.date().equals(
    // repayment.date()
    // )) {

    // /*
    // * This branch is intentionally handled
    // * below using maturity comparison.
    // */
    // }

    // result.add(repayment);
    // }

    // return result;
    // }

    private List<PrincipalRepayment> addRemainingToMaturity(
            List<PrincipalRepayment> repayments,
            LocalDate maturityDate,
            BigDecimal remaining) {

        List<PrincipalRepayment> result = new ArrayList<>();

        for (PrincipalRepayment repayment : repayments) {

            if (repayment.date().equals(maturityDate)) {

                result.add(
                        new PrincipalRepayment(
                                repayment.date(),
                                repayment.principalAmount()
                                        .add(remaining),
                                BigDecimal.ZERO));

            } else {

                result.add(repayment);
            }
        }

        return result;
    }

    private LocalDate parseDate(
            String value) {

        String[] parts = value.split("[/-]");

        if (parts.length != 3) {

            throw new IllegalArgumentException(
                    "Invalid maturity date: "
                            + value);
        }

        int day = Integer.parseInt(parts[0]);

        int month = Integer.parseInt(parts[1]);

        int year = Integer.parseInt(parts[2]);

        return LocalDate.of(
                year,
                month,
                day);
    }

    private String normalize(
            String value) {

        if (value == null) {
            return null;
        }

        return value
                .trim()
                .replaceAll("\\s+", " ");
    }

    private void validatePercentage(
            BigDecimal percentage) {

        if (percentage.compareTo(
                BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Amortization percentage must be positive");
        }

        if (percentage.compareTo(
                BigDecimal.valueOf(100)) > 0) {

            throw new IllegalArgumentException(
                    "Amortization percentage cannot exceed 100");
        }
    }

    private void validateInput(
            Bond bond,
            List<LocalDate> couponDates,
            LocalDate calculationDate) {

        if (bond == null) {

            throw new IllegalArgumentException(
                    "Bond cannot be null");
        }

        if (couponDates == null) {

            throw new IllegalArgumentException(
                    "Coupon dates cannot be null");
        }

        if (calculationDate == null) {

            throw new IllegalArgumentException(
                    "Calculation date cannot be null");
        }

        if (bond.getMaturityDate() == null) {

            throw new IllegalArgumentException(
                    "Maturity date is required");
        }
    }
}