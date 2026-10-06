package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Dto.ParsedLotSize;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.Bond.Exception.BondFieldParseException;

/**
 * Default {@link BondFieldParser}.
 *
 * <p>
 * Maturity text is handed to {@link MaturityDescriptionParser} rather than
 * re-implemented here, so the create/update path and the cash-flow engine agree
 * on what a maturity description means. The remaining grammars — the three
 * IP-date forms, the lakh/crore suffixes, the lot wording — mirror the
 * conventions documented on the {@code Bond} entity and are exercised by
 * {@code BondFieldParserTest}.
 */
@Service
public class BondFieldParserImpl implements BondFieldParser {

    private final MaturityDescriptionParser maturityDescriptionParser;
    private final CouponDateGenerator couponDateGenerator;

    public BondFieldParserImpl(
            MaturityDescriptionParser maturityDescriptionParser,
            CouponDateGenerator couponDateGenerator) {
        this.maturityDescriptionParser = maturityDescriptionParser;
        this.couponDateGenerator = couponDateGenerator;
    }

    // =========================
    // PATTERNS
    // =========================

    /**
     * A maturity description that spells out two dates, e.g.
     * {@code 9/11/2024 to 9/11/2033 (10% each year)} or
     * {@code 22/04/2029-27/08/2029 (20% every month)}. Such a bond is a range,
     * not a single-date amortizer. The date shape is required so that an
     * unrelated "to" in prose never classifies a bond as a range.
     */
    private static final Pattern MATURITY_RANGE = Pattern.compile(
            "\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}\\s*(?:to|[-\\u2013\\u2014])\\s*\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}",
            Pattern.CASE_INSENSITIVE);

    /**
     * A quantum, optionally with a lakh/crore suffix. Anything else that still
     * contains a number is rejected rather than guessed at, so a value the
     * parser does not understand never lands in the normalized column.
     */
    private static final Pattern QUANTUM_WITH_UNIT = Pattern.compile(
            "^\\s*(\\d+(?:[.,]\\d+)?)\\s*(crore|crores|cr|lakh|lakhs|lac|lacs)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LOT_WITH_UNIT = Pattern.compile(
            "^\\s*(\\d+(?:[.,]\\d+)?)\\s*(crore|crores|cr|lakh|lakhs|lac|lacs)?\\s*lots?\\b.*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * A lot described as a count of instruments rather than as "lots":
     * {@code 1000 Units}, {@code 500 Nos}, {@code 10 Lakh Units}. The size is
     * a unit count, so the type is NUMERIC rather than FIXED_LOT.
     */
    private static final Pattern LOT_UNITS = Pattern.compile(
            "^\\s*(\\d+(?:[.,]\\d+)?)\\s*(crore|crores|cr|lakh|lakhs|lac|lacs)?\\s*(units?|nos|no|bonds?)\\b.*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Lot wordings that carry no numeric size because the holding is electronic.
     * CDSL and NSDL are the Indian depositories, so they mean the same as DEMAT.
     */
    private static final Pattern DEMAT_LOT = Pattern.compile(
            "\\b(demat|cdsl|nsdl)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PLAIN_NUMBER = Pattern.compile(
            "^\\s*\\d+(?:[.,]\\d+)?\\s*$");

    /**
     * Values that stand in for "no numeric quantum" rather than an amount.
     */
    private static final Pattern NO_QUANTUM = Pattern.compile(
            "^\\s*(any|na|n/a|-)?\\s*$|\\bbonds?\\b",
            Pattern.CASE_INSENSITIVE);

    // =========================
    // ENUM ALIASES
    // =========================

    /**
     * Wordings the sheets use that are not the enum constant itself. Keys and
     * values are both in the {@link #normalizeToken} alphabet.
     */
    private static final Map<String, String> ENUM_ALIASES = Map.ofEntries(
            Map.entry("ANN", "YEARLY"),
            Map.entry("ANNUAL", "YEARLY"),
            Map.entry("ANNUALLY", "YEARLY"),
            Map.entry("EACH_YEAR", "YEARLY"),
            Map.entry("SEMI_ANNUAL", "HALF_YEARLY"),
            Map.entry("SEMIANNUAL", "HALF_YEARLY"),
            Map.entry("HALF_YEARLY", "HALF_YEARLY"),
            Map.entry("QUARTELY", "QUARTERLY"),
            Map.entry("QTR", "QUARTERLY"),
            Map.entry("PERP", "PERPETUAL"),
            Map.entry("AMORTISING", "AMORTIZING"),
            Map.entry("FIXED_LOT", "FIXED_LOT"),
            Map.entry("FIXED_LOT_SIZE", "FIXED_LOT"));

    // =========================
    // DATE FORMATS
    // =========================

    /**
     * Tried in order. Text-month patterns come after the numeric ones so that
     * {@code 01/10/2026} is never read as a month name, and before the
     * two-digit-year numeric ones so that {@code 7/Mar/28} is recognized.
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-M-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ENGLISH),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/MMM/uu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/MMM/uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-uu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/MMMM/uu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/MMMM/uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/uu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-M-uu", Locale.ENGLISH));

    // =========================
    // SCALARS
    // =========================

    @Override
    public BigDecimal parsePercentage(String raw, String field) {

        if (raw == null || raw.isBlank()) {
            throw new BondFieldParseException(field, raw, "a percentage is required");
        }

        String cleaned = raw.trim()
                .replace("%", "")
                .replace(",", "")
                .trim();

        try {
            return new BigDecimal(cleaned);

        } catch (NumberFormatException ex) {

            throw new BondFieldParseException(field, raw, "not a number", ex);
        }
    }

    @Override
    public BigDecimal parseDecimal(String raw, String field) {

        if (raw == null || raw.isBlank()) {
            throw new BondFieldParseException(field, raw, "a number is required");
        }

        try {
            return new BigDecimal(raw.trim().replace(",", ""));

        } catch (NumberFormatException ex) {

            throw new BondFieldParseException(field, raw, "not a number", ex);
        }
    }

    @Override
    public LocalDate parseDate(String raw, String field) {

        if (raw == null || raw.isBlank()) {
            throw new BondFieldParseException(field, raw, "a date is required");
        }

        String cleaned = raw.trim().replaceAll("\\s+", " ");

        for (DateTimeFormatter formatter : DATE_FORMATS) {

            try {
                return LocalDate.parse(cleaned, formatter);

            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }

        throw new BondFieldParseException(
                field,
                raw,
                "unrecognized date format. Expected forms like 7/Mar/28, 26-09-2031 or 2026-10-01");
    }

    @Override
    public <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {

        if (raw == null || raw.isBlank()) {
            throw new BondFieldParseException(field, raw, "a value is required");
        }

        String token = normalizeToken(raw);

        String resolved = ENUM_ALIASES.getOrDefault(token, token);

        try {
            return Enum.valueOf(type, resolved);

        } catch (IllegalArgumentException ex) {

            throw new BondFieldParseException(
                    field,
                    raw,
                    "not one of " + List.of(type.getEnumConstants()),
                    ex);
        }
    }

    // =========================
    // BOND-SPECIFIC
    // =========================

    @Override
    public SecurityType parseSecurityType(String raw) {
        return parseEnum(SecurityType.class, raw, "securityType");
    }

    @Override
    public CouponFrequency frequencyFromSchedule(String ipDateDescription) {

        // Delegated to the grammar owner, so create/update and the cash-flow
        // engine can never disagree about a schedule's frequency.
        return couponDateGenerator.frequencyOf(ipDateDescription);
    }

    @Override
    public CouponFrequency parseCouponFrequency(String raw) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        CouponFrequency inferred = inferCouponFrequency(raw);

        if (inferred != null) {
            return inferred;
        }

        return parseEnum(CouponFrequency.class, raw, "couponFrequency");
    }

    @Override
    public MaturitySchedule parseMaturity(String maturityDescription, LocalDate explicitMaturityDate) {

        if (maturityDescription == null || maturityDescription.isBlank()) {

            if (explicitMaturityDate == null) {
                return null;
            }

            return new MaturitySchedule(explicitMaturityDate, List.of(), false);
        }

        try {

            return maturityDescriptionParser.parse(maturityDescription, explicitMaturityDate);

        } catch (IllegalArgumentException ex) {

            throw new BondFieldParseException(
                    "maturityDescription",
                    maturityDescription,
                    ex.getMessage() == null ? "unsupported maturity text" : ex.getMessage(),
                    ex);
        }
    }

    @Override
    public MaturityType parseMaturityType(String raw) {
        return parseEnum(MaturityType.class, raw, "maturityType");
    }

    @Override
    public MaturityType deriveMaturityType(MaturitySchedule schedule, String maturityDescription) {

        if (schedule == null) {
            return null;
        }

        if (schedule.perpetual()) {
            return MaturityType.PERPETUAL;
        }

        if (schedule.isAmortizing()) {

            boolean spansRange = maturityDescription != null
                    && MATURITY_RANGE.matcher(maturityDescription).find();

            return spansRange ? MaturityType.RANGE : MaturityType.AMORTIZING;
        }

        return MaturityType.FIXED;
    }

    @Override
    public BigDecimal parseQuantumInLacs(String raw) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim();

        if (NO_QUANTUM.matcher(normalized).find()) {
            return null;
        }

        Matcher matcher = QUANTUM_WITH_UNIT.matcher(normalized);

        if (!matcher.matches()) {

            throw new BondFieldParseException(
                    "quantumDescription",
                    raw,
                    "expected an amount like '3 Lakh', '1.50 Lakh' or '1 Crore'");
        }

        BigDecimal value = toNumber(matcher.group(1), "quantumDescription", raw);

        String unit = matcher.group(2);

        // The normalized column is in lakhs, so only a crore suffix scales.
        if (unit != null && (unit.equalsIgnoreCase("crore")
                || unit.equalsIgnoreCase("crores")
                || unit.equalsIgnoreCase("cr"))) {

            value = value.multiply(BigDecimal.valueOf(100));
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public ParsedLotSize parseLotSize(String raw) {

        if (raw == null || raw.isBlank()) {
            return ParsedLotSize.unknown();
        }

        String normalized = raw.trim().toLowerCase(Locale.ENGLISH);

        if (DEMAT_LOT.matcher(normalized).find()) {
            return ParsedLotSize.of(null, LotSizeType.DEMAT);
        }

        if (normalized.contains("sgl")) {
            return ParsedLotSize.of(null, LotSizeType.SGL);
        }

        Matcher lotMatcher = LOT_WITH_UNIT.matcher(normalized);

        if (lotMatcher.matches()) {

            return ParsedLotSize.of(
                    scaledLot(lotMatcher.group(1), lotMatcher.group(2), raw),
                    LotSizeType.FIXED_LOT);
        }

        Matcher unitMatcher = LOT_UNITS.matcher(normalized);

        if (unitMatcher.matches()) {

            return ParsedLotSize.of(
                    scaledLot(unitMatcher.group(1), unitMatcher.group(2), raw),
                    LotSizeType.NUMERIC);
        }

        if (PLAIN_NUMBER.matcher(normalized).matches()) {

            return ParsedLotSize.of(
                    toNumber(normalized, "lotSizeDescription", raw).setScale(0, RoundingMode.HALF_UP),
                    LotSizeType.NUMERIC);
        }

        return ParsedLotSize.unknown();
    }

    /**
     * A lot count with an optional lakh/crore multiplier, resolved to a whole
     * number of instruments.
     */
    private BigDecimal scaledLot(String number, String unit, String raw) {

        return toNumber(number, "lotSizeDescription", raw)
                .multiply(multiplierFor(unit))
                .setScale(0, RoundingMode.HALF_UP);
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Infers a frequency from a description that names one in words rather than
     * matching one of {@link CouponDateGenerator}'s schedule grammars —
     * {@code "Annually"}, {@code "Quarterly"}, {@code "Semi-Annual"}.
     *
     * <p>
     * Returns {@code null} when the text does not identify one, so that a
     * stored-only description is never rejected here — the cash-flow engine
     * decides later whether it can generate dates from it.
     */
    private CouponFrequency inferCouponFrequency(String text) {

        if (text == null || text.isBlank()) {
            return null;
        }

        String normalized = text.toLowerCase(Locale.ENGLISH);

        if (normalized.contains("ann") || normalized.contains("annual") || normalized.contains("year")) {
            return CouponFrequency.YEARLY;
        }

        if (normalized.contains("quart")) {
            return CouponFrequency.QUARTERLY;
        }

        if (normalized.contains("half") || normalized.contains("semi")) {
            return CouponFrequency.HALF_YEARLY;
        }

        if (normalized.contains("month")) {
            return CouponFrequency.MONTHLY;
        }

        return null;
    }

    private BigDecimal multiplierFor(String unit) {

        if (unit == null || unit.isBlank()) {
            return BigDecimal.ONE;
        }

        return switch (unit.toLowerCase(Locale.ENGLISH)) {

            case "crore", "crores", "cr" -> BigDecimal.valueOf(10_000_000L);

            case "lakh", "lakhs", "lac", "lacs" -> BigDecimal.valueOf(100_000L);

            default -> BigDecimal.ONE;
        };
    }

    private BigDecimal toNumber(String value, String field, String raw) {

        try {
            return new BigDecimal(value.replace(",", ""));

        } catch (NumberFormatException ex) {

            throw new BondFieldParseException(field, raw, "not a number", ex);
        }
    }

    /**
     * Upper-cases and collapses every run of non-alphanumerics to a single
     * underscore, so {@code "half-yearly"} and {@code "Half Yearly"} both become
     * {@code HALF_YEARLY}.
     */
    private String normalizeToken(String raw) {

        return raw.trim()
                .toUpperCase(Locale.ENGLISH)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
