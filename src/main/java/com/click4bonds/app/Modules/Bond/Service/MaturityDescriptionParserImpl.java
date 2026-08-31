package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;

@Service
public class MaturityDescriptionParserImpl implements MaturityDescriptionParser {
    /*
     * * ------------------------------------------------------------ * Normal
     * maturity: * * 26-09-2031 * 26/09/2031 *
     * ------------------------------------------------------------
     */ private static final Pattern DATE_ONLY = Pattern.compile("^\\s*(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})\\s*$");
    /*
     * * ------------------------------------------------------------ * Perpetual: *
     * * Perp * Perpetual *
     * ------------------------------------------------------------
     */ private static final Pattern PERPETUAL = Pattern.compile("^\\s*perp(?:etual)?\\s*$", Pattern.CASE_INSENSITIVE);
    /*
     * * ------------------------------------------------------------ * Simple
     * frequency amortization: * * 9/11/2024 to 9/11/2033 (10% each year) * *
     * 01/10/2026 to 01/10/2027 (20% Quarterly) * * Also accepts: * * Quartely *
     * ------------------------------------------------------------
     */ private static final Pattern FIXED_FREQUENCY = Pattern.compile(
            "^\\s*" + "(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})" + "\\s+to\\s+" + "(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})"
                    + "\\s*\\(" + "\\s*(\\d+(?:\\.\\d+)?)%" + "\\s*" + "(each\\s+year|yearly|annual|"
                    + "quarterly|quartely|" + "half[- ]?yearly|semi[- ]?annual|" + "monthly)" + "\\s*\\)" + "\\s*$",
            Pattern.CASE_INSENSITIVE);
    /*
     * * ------------------------------------------------------------ * IP-based
     * amortization: * * 26-09-2031 * (2.5% on Each IP till 2027) * * This parser is
     * deliberately used after the maturity date. * *
     * ------------------------------------------------------------
     */ private static final Pattern IP_RULE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)%" + "\\s*on\\s*each\\s*ip" + "\\s*(?:till|until|upto)" + "\\s*(\\d{4})",
            Pattern.CASE_INSENSITIVE);
    /*
     * * ------------------------------------------------------------ * Staged IP
     * amortization: * * 26-09-2031 * (2.5% on Each IP till 2027 * and 10% on Each
     * IP from 2028 to 2031) * *
     * ------------------------------------------------------------
     */ private static final Pattern STAGED_IP_RULE = Pattern.compile("(\\d+(?:\\.\\d+)?)%" + "\\s*on\\s*each\\s*ip"
            + "\\s*from\\s*(\\d{4})" + "\\s*(?:to|till|until)" + "\\s*(\\d{4})", Pattern.CASE_INSENSITIVE);

    @Override
    public MaturitySchedule parse(String maturityDescription, LocalDate normalizedMaturityDate) {
        if (maturityDescription == null || maturityDescription.isBlank()) {
            return normalSchedule(normalizedMaturityDate);
        }
        String description = normalize(maturityDescription);
        /*
         * * -------------------------------------------------------- * PERPETUAL *
         * --------------------------------------------------------
         */ if (PERPETUAL.matcher(description).matches()) {
            return new MaturitySchedule(null, List.of(), true);
        }
        /*
         * * -------------------------------------------------------- * DATE ONLY *
         * --------------------------------------------------------
         */ Matcher dateMatcher = DATE_ONLY.matcher(description);
        if (dateMatcher.matches()) {
            LocalDate parsedDate = parseDate(dateMatcher.group(1), dateMatcher.group(2), dateMatcher.group(3));
            return normalSchedule(parsedDate);
        }
        /*
         * * -------------------------------------------------------- * FIXED FREQUENCY
         * AMORTIZATION * --------------------------------------------------------
         */ Matcher fixedMatcher = FIXED_FREQUENCY.matcher(description);
        if (fixedMatcher.matches()) {
            LocalDate startDate = parseDate(fixedMatcher.group(1), fixedMatcher.group(2), fixedMatcher.group(3));
            LocalDate endDate = parseDate(fixedMatcher.group(4), fixedMatcher.group(5), fixedMatcher.group(6));
            BigDecimal percentage = new BigDecimal(fixedMatcher.group(7));
            CouponFrequency frequency = parseFrequency(fixedMatcher.group(8));
            return new MaturitySchedule(endDate,
                    List.of(new FixedFrequencyAmortizationRule(percentage, startDate, endDate, frequency)), false);
        }
        /*
         * * -------------------------------------------------------- * STAGED IP
         * AMORTIZATION * * We first extract the maturity date from the * beginning of
         * the description. * --------------------------------------------------------
         */ LocalDate maturityDate = extractLeadingDate(description);
        if (maturityDate != null) {
            List<AmortizationRule> rules = parseIpRules(description, maturityDate);
            if (!rules.isEmpty()) {
                return new MaturitySchedule(maturityDate, rules, false);
            }
        }
        throw new IllegalArgumentException("Unsupported maturity description: " + maturityDescription);
    }

    /*
     * * ============================================================ * IP RULE
     * PARSING * ============================================================
     */ private List<AmortizationRule> parseIpRules(String description, LocalDate maturityDate) {
        List<AmortizationRule> rules = new ArrayList<>();
        /* * Example: * * 2.5% on Each IP till 2027 */ Matcher untilMatcher = IP_RULE.matcher(description);
        while (untilMatcher.find()) {
            BigDecimal percentage = new BigDecimal(untilMatcher.group(1));
            int endYear = Integer.parseInt(untilMatcher.group(2));
            LocalDate startDate = LocalDate.of(maturityDate.getYear() - 100, 1, 1);
            LocalDate endDate = LocalDate.of(endYear, 12, 31);
            rules.add(new IpBasedAmortizationRule(percentage, startDate, endDate));
        }
        /* * Example: * * 10% on Each IP from 2028 to 2031 */ Matcher stagedMatcher = STAGED_IP_RULE
                .matcher(description);
        while (stagedMatcher.find()) {
            BigDecimal percentage = new BigDecimal(stagedMatcher.group(1));
            int startYear = Integer.parseInt(stagedMatcher.group(2));
            int endYear = Integer.parseInt(stagedMatcher.group(3));
            LocalDate startDate = LocalDate.of(startYear, 1, 1);
            LocalDate endDate = LocalDate.of(endYear, 12, 31);
            rules.add(new IpBasedAmortizationRule(percentage, startDate, endDate));
        }
        return rules;
    }

    /*
     * * ============================================================ * HELPERS *
     * ============================================================
     */ private MaturitySchedule normalSchedule(LocalDate maturityDate) {
        if (maturityDate == null) {
            throw new IllegalArgumentException("Maturity date is required");
        }
        return new MaturitySchedule(maturityDate, List.of(), false);
    }

    private LocalDate extractLeadingDate(String description) {
        Matcher matcher = Pattern.compile("^\\s*(\\d{1,2})[-/]" + "(\\d{1,2})[-/]" + "(\\d{4})").matcher(description);
        if (!matcher.find()) {
            return null;
        }
        return parseDate(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private LocalDate parseDate(String day, String month, String year) {
        return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
    }

    private CouponFrequency parseFrequency(String value) {
        String normalized = value.trim().toLowerCase().replaceAll("\\s+", " ");
        return switch (normalized) {
            case "each year", "yearly", "annual" -> CouponFrequency.YEARLY;
            case "quarterly", "quartely" -> CouponFrequency.QUARTERLY;
            case "half-yearly", "half yearly", "semi-annual" -> CouponFrequency.HALF_YEARLY;
            case "monthly" -> CouponFrequency.MONTHLY;
            default -> throw new IllegalArgumentException("Unsupported amortization frequency: " + value);
        };
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}
