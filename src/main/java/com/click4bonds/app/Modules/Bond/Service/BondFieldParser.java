package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Dto.ParsedLotSize;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;

/**
 * Turns the loosely-typed values an admin sends on the bond create/update
 * endpoints into the normalized values the {@code Bond} entity stores.
 *
 * <p>
 * The admin UI forwards whatever the source sheet held — {@code "8.45%"},
 * {@code "Secured"}, {@code "7/Mar/28"}, {@code "15/Oct/33"},
 * {@code "1.50 Lakh"}, {@code "10 Lacs Lot"}, {@code "1st of Every Month"} —
 * and this parser is the single place that interprets them. Every method throws
 * {@link com.click4bonds.app.Modules.Bond.Exception.BondFieldParseException}
 * naming the field when a value is present but cannot be read; callers that
 * tolerate blanks must check for blank before calling.
 */
public interface BondFieldParser {

    // =========================
    // SCALARS
    // =========================

    /**
     * Parses a percentage, accepting {@code "8.45%"}, {@code "8.45 %"} and
     * {@code "8.45"}. Returned as a percentage value (8.45, not 0.0845), which
     * is how {@code Bond.couponRate} and the YTM columns are stored.
     */
    BigDecimal parsePercentage(String raw, String field);

    /**
     * Parses a plain decimal, accepting {@code "102.08"} and {@code "102.08 "}.
     */
    BigDecimal parseDecimal(String raw, String field);

    /**
     * Parses a date in any of the formats the source sheets use:
     * {@code 7/Mar/28}, {@code 15/Oct/33}, {@code 26-09-2031},
     * {@code 01/10/2026} and ISO {@code 2026-10-01}. Two-digit years are read
     * as 20xx.
     */
    LocalDate parseDate(String raw, String field);

    /**
     * Parses an enum leniently: exact match, then case-insensitive, then a
     * small alias table (e.g. {@code ANNUAL -> YEARLY},
     * {@code SEMI_ANNUAL -> HALF_YEARLY}).
     */
    <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field);

    // =========================
    // BOND-SPECIFIC
    // =========================

    /**
     * Parses {@code "Secured"} / {@code "Unsecured"} (or the enum names).
     */
    SecurityType parseSecurityType(String raw);

    /**
     * The frequency implied by the schedule grammar of an IP-date description,
     * or {@code null} when the text is not one of the supported schedules.
     *
     * <p>
     * Grammar-exact: it recognizes the schedule forms
     * ({@code dd/mm-dd/mm}, {@code dd/mm Ann}, {@code Nth of every month}) and
     * never guesses from keywords. Callers that treat the description as the
     * source of truth use this so a guess can never override an explicit value.
     */
    CouponFrequency frequencyFromSchedule(String ipDateDescription);

    /**
     * Parses an explicit frequency token such as {@code "Ann"} or
     * {@code "Quarterly"}, falling back to keyword inference for wordings that
     * are not schedule grammar.
     *
     * @return the frequency, or {@code null} when the text is blank or names
     *         none
     */
    CouponFrequency parseCouponFrequency(String raw);

    /**
     * Delegates to {@link MaturityDescriptionParser} to turn the maturity text
     * into a date (and, for amortizing bonds, repayment rules).
     *
     * @param maturityDescription raw text, may be blank
     * @param explicitMaturityDate a date sent separately, may be null
     */
    MaturitySchedule parseMaturity(String maturityDescription, LocalDate explicitMaturityDate);

    /**
     * Parses an explicitly supplied maturity type such as {@code "Fixed"}.
     */
    MaturityType parseMaturityType(String raw);

    /**
     * Derives the maturity type from the parsed schedule and the raw text when
     * the caller did not supply one: perpetual, a {@code start to end} range, an
     * amortizing schedule, or a plain fixed date.
     */
    MaturityType deriveMaturityType(MaturitySchedule schedule, String maturityDescription);

    /**
     * Normalizes a quantum into lakhs: {@code "1.50 Lakh" -> 1.50},
     * {@code "70 Lakh" -> 70}, {@code "1 Crore" -> 100}.
     *
     * @return the value in lakhs, or {@code null} when the text carries no
     *         numeric quantum ({@code "Any"}, {@code "1 Bonds"}, blank)
     */
    BigDecimal parseQuantumInLacs(String raw);

    /**
     * Normalizes a lot description: {@code "Demat" -> (null, DEMAT)},
     * {@code "10 Lacs Lot" -> (1000000, FIXED_LOT)}, {@code "775 Lot" ->
     * (775, FIXED_LOT)}.
     */
    ParsedLotSize parseLotSize(String raw);
}
