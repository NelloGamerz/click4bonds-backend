package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Dto.ParsedLotSize;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.Bond.Exception.BondFieldParseException;

/**
 * Covers the raw values the admin UI forwards from the source sheet. The
 * examples are taken from real rows, so a regression here means the create or
 * update endpoint would store a wrong normalized column.
 */
class BondFieldParserTest {

    private BondFieldParser parser;

    @BeforeEach
    void setUp() {
        parser = new BondFieldParserImpl(
                new MaturityDescriptionParserImpl(),
                new CouponDateGenerator());
    }

    // =========================================================
    // PERCENTAGES
    // =========================================================

    @Test
    void shouldParsePercentageWithoutSign() {
        assertEquals(0, new BigDecimal("8.45")
                .compareTo(parser.parsePercentage("8.45", "couponRate")));
    }

    @Test
    void shouldParsePercentageWithSignAndSpaces() {
        assertEquals(0, new BigDecimal("0.40")
                .compareTo(parser.parsePercentage(" 0.40% ", "couponRate")));
        assertEquals(0, new BigDecimal("15.90")
                .compareTo(parser.parsePercentage("15.90%", "annualYtm")));
    }

    @Test
    void shouldRejectNonNumericPercentage() {
        assertThrows(
                BondFieldParseException.class,
                () -> parser.parsePercentage("NA", "couponRate"));
    }

    // =========================================================
    // ENUMS
    // =========================================================

    @Test
    void shouldParseSecurityTypeCaseInsensitively() {
        assertEquals(SecurityType.SECURED, parser.parseSecurityType("Secured"));
        assertEquals(SecurityType.SECURED, parser.parseSecurityType("SECURED"));
        assertEquals(SecurityType.UNSECURED, parser.parseSecurityType("unsecured"));
    }

    @Test
    void shouldParseEnumAliases() {
        assertEquals(CouponFrequency.YEARLY,
                parser.parseEnum(CouponFrequency.class, "Annual", "couponFrequency"));
        assertEquals(CouponFrequency.HALF_YEARLY,
                parser.parseEnum(CouponFrequency.class, "Semi-Annual", "couponFrequency"));
        assertEquals(CouponFrequency.QUARTERLY,
                parser.parseEnum(CouponFrequency.class, "Quartely", "couponFrequency"));
        assertEquals(MaturityType.PERPETUAL,
                parser.parseEnum(MaturityType.class, "Perp", "maturityType"));
    }

    @Test
    void shouldRejectUnknownEnumValue() {
        assertThrows(
                BondFieldParseException.class,
                () -> parser.parseSecurityType("Sort Of Secured"));
    }

    // =========================================================
    // DATES
    // =========================================================

    @Test
    void shouldParseTextMonthDates() {
        assertEquals(LocalDate.of(2028, 3, 7), parser.parseDate("7/Mar/28", "maturityDate"));
        assertEquals(LocalDate.of(2033, 10, 15), parser.parseDate("15/Oct/33", "maturityDate"));
        assertEquals(LocalDate.of(2031, 7, 23), parser.parseDate("23-Jul-2031", "maturityDate"));
    }

    @Test
    void shouldParseNumericAndIsoDates() {
        assertEquals(LocalDate.of(2031, 9, 26), parser.parseDate("26-09-2031", "maturityDate"));
        assertEquals(LocalDate.of(2026, 10, 1), parser.parseDate("01/10/2026", "maturityDate"));
        assertEquals(LocalDate.of(2026, 10, 1), parser.parseDate("2026-10-01", "maturityDate"));
    }

    @Test
    void shouldRejectUnknownDate() {
        assertThrows(
                BondFieldParseException.class,
                () -> parser.parseDate("next Diwali", "maturityDate"));
    }

    // =========================================================
    // COUPON FREQUENCY
    // =========================================================

    @Test
    void shouldReadFrequencyFromTheScheduleGrammar() {
        assertEquals(CouponFrequency.HALF_YEARLY,
                parser.frequencyFromSchedule("07/03-07/09"));
        assertEquals(CouponFrequency.HALF_YEARLY,
                parser.frequencyFromSchedule("28/03-28/09"));
        assertEquals(CouponFrequency.YEARLY,
                parser.frequencyFromSchedule("15/10 Ann"));
        assertEquals(CouponFrequency.YEARLY,
                parser.frequencyFromSchedule("09/11 Ann"));
        assertEquals(CouponFrequency.MONTHLY,
                parser.frequencyFromSchedule("1st of Every Month"));
    }

    @Test
    void shouldReadFrequencyFromAlternateScheduleSeparators() {
        assertEquals(CouponFrequency.HALF_YEARLY,
                parser.frequencyFromSchedule("09/02 & 09/08"));
        assertEquals(CouponFrequency.HALF_YEARLY,
                parser.frequencyFromSchedule("09/02 – 09/08"));
    }

    @Test
    void shouldParseExplicitFrequencyToken() {
        assertEquals(CouponFrequency.QUARTERLY, parser.parseCouponFrequency("Quarterly"));
        assertEquals(CouponFrequency.MONTHLY, parser.parseCouponFrequency("MONTHLY"));
        assertEquals(CouponFrequency.YEARLY, parser.parseCouponFrequency("Ann"));
    }

    @Test
    void shouldReturnNullFrequencyWhenUnknown() {
        assertNull(parser.frequencyFromSchedule(null));
        assertNull(parser.frequencyFromSchedule("not a schedule"));
        assertNull(parser.parseCouponFrequency(null));
        assertNull(parser.parseCouponFrequency(""));
    }

    // =========================================================
    // QUANTUM
    // =========================================================

    @Test
    void shouldParseQuantumInLakhs() {
        assertEquals(0, new BigDecimal("1.50")
                .compareTo(parser.parseQuantumInLacs("1.50 Lakh")));
        assertEquals(0, new BigDecimal("70.00")
                .compareTo(parser.parseQuantumInLacs("70 Lakh")));
        assertEquals(0, new BigDecimal("76.89")
                .compareTo(parser.parseQuantumInLacs("76.89 Lakh")));
        assertEquals(0, new BigDecimal("100.00")
                .compareTo(parser.parseQuantumInLacs("1 Crore")));
    }

    @Test
    void shouldTreatUnquantifiedTextAsNoQuantum() {
        assertNull(parser.parseQuantumInLacs("Any"));
        assertNull(parser.parseQuantumInLacs("1 Bonds"));
        assertNull(parser.parseQuantumInLacs("NA"));
        assertNull(parser.parseQuantumInLacs(null));
    }

    @Test
    void shouldRejectUnrecognizedQuantum() {
        assertThrows(
                BondFieldParseException.class,
                () -> parser.parseQuantumInLacs("50 Apples"));
    }

    // =========================================================
    // LOT SIZE
    // =========================================================

    @Test
    void shouldParseDematAndSglLots() {
        ParsedLotSize demat = parser.parseLotSize("Demat");
        assertNull(demat.lotSize());
        assertEquals(LotSizeType.DEMAT, demat.lotSizeType());

        ParsedLotSize sgl = parser.parseLotSize("SGL");
        assertNull(sgl.lotSize());
        assertEquals(LotSizeType.SGL, sgl.lotSizeType());
    }

    @Test
    void shouldParseNumericLots() {
        assertEquals(0, new BigDecimal("775")
                .compareTo(parser.parseLotSize("775 Lot").lotSize()));
        assertEquals(0, new BigDecimal("800")
                .compareTo(parser.parseLotSize("800 Lot").lotSize()));
        assertEquals(0, new BigDecimal("10000")
                .compareTo(parser.parseLotSize("10000 Lot").lotSize()));
        assertEquals(LotSizeType.FIXED_LOT, parser.parseLotSize("775 Lot").lotSizeType());
    }

    @Test
    void shouldParseLakhAndCroreLots() {
        assertEquals(0, new BigDecimal("1000000")
                .compareTo(parser.parseLotSize("10 Lacs Lot").lotSize()));
        assertEquals(0, new BigDecimal("1000000")
                .compareTo(parser.parseLotSize("10 Lakh Lot").lotSize()));
        assertEquals(0, new BigDecimal("10000000")
                .compareTo(parser.parseLotSize("1 Crore Lot").lotSize()));
    }

    @Test
    void shouldTreatDepositoryHoldingsAsDemat() {
        assertEquals(LotSizeType.DEMAT, parser.parseLotSize("CDSL").lotSizeType());
        assertEquals(LotSizeType.DEMAT, parser.parseLotSize("NSDL Demat").lotSizeType());
        assertNull(parser.parseLotSize("CDSL").lotSize());
    }

    @Test
    void shouldParseLotsExpressedAsUnitCounts() {
        ParsedLotSize units = parser.parseLotSize("1000 Units");

        assertEquals(0, new BigDecimal("1000").compareTo(units.lotSize()));
        assertEquals(LotSizeType.NUMERIC, units.lotSizeType());

        ParsedLotSize lakhUnits = parser.parseLotSize("10 Lakh Units");

        assertEquals(0, new BigDecimal("1000000").compareTo(lakhUnits.lotSize()));
        assertEquals(LotSizeType.NUMERIC, lakhUnits.lotSizeType());
    }

    @Test
    void shouldRecognizePluralLotWording() {
        assertEquals(0, new BigDecimal("500")
                .compareTo(parser.parseLotSize("500 Lots").lotSize()));
        assertEquals(LotSizeType.FIXED_LOT, parser.parseLotSize("500 Lots").lotSizeType());
    }

    // =========================================================
    // MATURITY
    // =========================================================

    @Test
    void shouldParsePlainMaturityDate() {
        MaturitySchedule schedule = parser.parseMaturity("7/Mar/28", null);

        assertEquals(LocalDate.of(2028, 3, 7), schedule.maturityDate());
        assertEquals(MaturityType.FIXED,
                parser.deriveMaturityType(schedule, "7/Mar/28"));
    }

    @Test
    void shouldParsePerpetualMaturity() {
        MaturitySchedule schedule = parser.parseMaturity("Perp", null);

        assertTrue(schedule.perpetual());
        assertEquals(MaturityType.PERPETUAL,
                parser.deriveMaturityType(schedule, "Perp"));
    }

    @Test
    void shouldParsePerpetualWordingVariants() {
        assertTrue(parser.parseMaturity("Perpetual", null).perpetual());
        assertTrue(parser.parseMaturity("Perp.", null).perpetual());
        assertTrue(parser.parseMaturity("  perp  ", null).perpetual());
    }

    @Test
    void shouldParseFixedFrequencyRangeAsRange() {
        String description = "9/11/2024 to 9/11/2033 (10% each year)";

        MaturitySchedule schedule = parser.parseMaturity(description, null);

        assertEquals(LocalDate.of(2033, 11, 9), schedule.maturityDate());
        assertEquals(MaturityType.RANGE,
                parser.deriveMaturityType(schedule, description));
    }

    @Test
    void shouldParseExplicitRangeWithMisspelledQuarterly() {
        String description = "01/10/2026 to 01/10/2027 (20% Quartely)";

        MaturitySchedule schedule = parser.parseMaturity(description, null);

        assertEquals(LocalDate.of(2027, 10, 1), schedule.maturityDate());
        assertEquals(MaturityType.RANGE,
                parser.deriveMaturityType(schedule, description));
    }

    @Test
    void shouldParseDashSeparatedRangeWithEveryMonthFrequency() {
        String description = "22/04/2029-27/08/2029 (20% every month)";

        MaturitySchedule schedule = parser.parseMaturity(description, null);

        assertEquals(LocalDate.of(2029, 8, 27), schedule.maturityDate());
        assertEquals(1, schedule.amortizationRules().size());
        assertEquals(MaturityType.RANGE,
                parser.deriveMaturityType(schedule, description));
    }

    @Test
    void shouldParseIpBasedMaturityAsAmortizing() {
        String description = "26-09-2031 (2.5% on Each IP till 2027 (from 26-3-2022) then 7.5% on each IP)";

        MaturitySchedule schedule = parser.parseMaturity(description, null);

        assertEquals(LocalDate.of(2031, 9, 26), schedule.maturityDate());
        assertEquals(MaturityType.AMORTIZING,
                parser.deriveMaturityType(schedule, description));
    }

    @Test
    void shouldUseExplicitDateWhenDescriptionOmitted() {
        MaturitySchedule schedule = parser.parseMaturity(null, LocalDate.of(2030, 1, 1));

        assertEquals(LocalDate.of(2030, 1, 1), schedule.maturityDate());
        assertEquals(MaturityType.FIXED,
                parser.deriveMaturityType(schedule, null));
    }

    @Test
    void shouldReturnNullScheduleWhenNothingSupplied() {
        assertNull(parser.parseMaturity(null, null));
        assertNull(parser.deriveMaturityType(null, null));
    }

    @Test
    void shouldRejectUnsupportedMaturityDescription() {
        assertThrows(
                BondFieldParseException.class,
                () -> parser.parseMaturity("some random maturity description", null));
    }

    @Test
    void shouldRejectMaturityDateOnlyWhenUnparseable() {
        assertThrows(
                BondFieldParseException.class,
                () -> parser.parseMaturity("whenever", null));
    }
}
