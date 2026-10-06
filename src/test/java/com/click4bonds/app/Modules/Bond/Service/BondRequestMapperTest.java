package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.CreateBondRequest;
import com.click4bonds.app.Modules.Bond.Dto.UpdateBondRequest;
import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.Bond.Exception.BondFieldParseException;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * Verifies that a raw sheet row becomes the right columns on the entity, and
 * that update parsing keeps the "null means leave untouched" contract.
 */
class BondRequestMapperTest {

    private BondRequestMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BondRequestMapper(
                new BondFieldParserImpl(
                        new MaturityDescriptionParserImpl(),
                        new CouponDateGenerator()));
    }

    // =========================================================
    // CREATE
    // =========================================================

    @Test
    void shouldNormalizeARawSheetRowOnCreate() {

        CreateBondRequest request = new CreateBondRequest();

        request.setName("8.45% HAR SDL 2028");
        request.setIsin("in1620170143");
        request.setSecurityType("Secured");
        request.setCouponRate("8.45%");
        request.setRating("Sovereign");
        request.setRatingAgency("Govt Of India");
        request.setMaturityDescription("7/Mar/28");
        request.setPutCallDescription("NA");
        request.setPrice("102.08");
        request.setSemiYtm("6.88%");
        request.setAnnualYtm("7.00%");
        request.setIpDateDescription("07/03-07/09");
        request.setQuantumDescription("1.50 Lakh");
        request.setLotSizeDescription("Demat");

        Bond bond = mapper.toEntity(request, null);

        assertEquals("IN1620170143", bond.getIsin());
        assertEquals(SecurityType.SECURED, bond.getSecurityType());
        assertEquals(0, new BigDecimal("8.45").compareTo(bond.getCouponRate()));
        assertEquals(CouponFrequency.HALF_YEARLY, bond.getCouponFrequency());
        assertEquals(LocalDate.of(2028, 3, 7), bond.getMaturityDate());
        assertEquals(MaturityType.FIXED, bond.getMaturityType());
        assertEquals(0, new BigDecimal("102.08").compareTo(bond.getPrice()));
        assertEquals(0, new BigDecimal("6.88").compareTo(bond.getSemiYtm()));
        assertEquals(0, new BigDecimal("7.00").compareTo(bond.getAnnualYtm()));
        assertNotNull(bond.getYtmCalculatedAt());
        assertEquals(0, new BigDecimal("1.50").compareTo(bond.getQuantumInLacs()));
        assertNull(bond.getLotSize());
        assertEquals(LotSizeType.DEMAT, bond.getLotSizeType());
        assertEquals(BondStatus.DRAFT, bond.getStatus());
    }

    @Test
    void shouldLeaveQuantumNullForBondCountRows() {

        CreateBondRequest request = new CreateBondRequest();

        request.setName("0.40% PIDB 2033");
        request.setIsin("INE091D11204");
        request.setSecurityType("Secured");
        request.setCouponRate("0.40%");
        request.setMaturityDescription("15/Oct/33");
        request.setAnnualYtm("9.50%");
        request.setIpDateDescription("15/10 Ann");
        request.setQuantumDescription("1 Bonds");
        request.setLotSizeDescription("10 Lacs Lot");

        Bond bond = mapper.toEntity(request, null);

        assertNull(bond.getQuantumInLacs());
        assertEquals(LocalDate.of(2033, 10, 15), bond.getMaturityDate());
        assertEquals(CouponFrequency.YEARLY, bond.getCouponFrequency());
        assertEquals(0, new BigDecimal("1000000").compareTo(bond.getLotSize()));
        assertEquals(LotSizeType.FIXED_LOT, bond.getLotSizeType());
    }

    @Test
    void shouldLetTheDescriptionWinOverContradictingCompanions() {

        CreateBondRequest request = new CreateBondRequest();

        request.setName("13.00% Regency Fincorp Ltd 2029");
        request.setIsin("INE964R07143");
        request.setSecurityType("SECURED");
        request.setCouponRate("13.0");
        request.setCouponFrequency("MONTHLY");
        request.setIpDateDescription("22/04/2029-27/08/2029 (20% every month)");
        request.setMaturityType("FIXED");
        request.setMaturityDate("2029-08-27");
        request.setMaturityDescription("22/04/2029-27/08/2029 (20% every month)");
        request.setQuantumDescription("50 Lakh");
        request.setQuantumInLacs("50");
        request.setLotSizeDescription("10000 Lot");
        request.setLotSize("10000");
        request.setLotSizeType("DEMAT");

        Bond bond = mapper.toEntity(request, null);

        assertEquals(MaturityType.RANGE, bond.getMaturityType());
        assertEquals(LocalDate.of(2029, 8, 27), bond.getMaturityDate());
        assertEquals(0, new BigDecimal("10000").compareTo(bond.getLotSize()));
        assertEquals(LotSizeType.FIXED_LOT, bond.getLotSizeType());
        assertEquals(0, new BigDecimal("50.00").compareTo(bond.getQuantumInLacs()));
        assertEquals(CouponFrequency.MONTHLY, bond.getCouponFrequency());
    }

    @Test
    void shouldTreatAnUnquantifiedDescriptionAsNoQuantum() {

        CreateBondRequest request = new CreateBondRequest();

        request.setName("Bond count row");
        request.setIsin("INE000000003");
        request.setSecurityType("Secured");
        request.setCouponRate("9.00%");
        request.setMaturityDescription("7/Mar/28");
        request.setQuantumDescription("1 Bonds");
        request.setQuantumInLacs("50");

        // The description is authoritative: "1 Bonds" means the amount is not
        // expressed in lakhs, so the companion is not used to fill it in.
        assertNull(mapper.toEntity(request, null).getQuantumInLacs());
    }

    @Test
    void shouldFallBackToExplicitMaturityDateWhenDescriptionSaysNothing() {

        CreateBondRequest request = new CreateBondRequest();

        request.setName("NA maturity row");
        request.setIsin("INE000000004");
        request.setSecurityType("Secured");
        request.setCouponRate("9.00%");
        request.setMaturityDescription("NA");
        request.setMaturityDate("7/Mar/28");

        Bond bond = mapper.toEntity(request, null);

        assertEquals(LocalDate.of(2028, 3, 7), bond.getMaturityDate());
        assertEquals(MaturityType.FIXED, bond.getMaturityType());
    }

    @Test
    void shouldRejectMissingMaturity() {

        CreateBondRequest request = new CreateBondRequest();

        request.setName("No maturity");
        request.setIsin("INE000000001");
        request.setSecurityType("Secured");
        request.setCouponRate("8.00%");

        assertThrows(BadRequestException.class, () -> mapper.toEntity(request, null));
    }

    @Test
    void shouldRejectUnparseableCouponRate() {

        CreateBondRequest request = new CreateBondRequest();

        request.setName("Bad coupon");
        request.setIsin("INE000000002");
        request.setSecurityType("Secured");
        request.setCouponRate("tbd");
        request.setMaturityDescription("7/Mar/28");

        assertThrows(BondFieldParseException.class, () -> mapper.toEntity(request, null));
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Test
    void shouldLeaveFieldsUntouchedWhenRequestFieldIsNull() {

        Bond bond = existingBond();

        mapper.applyUpdate(bond, new UpdateBondRequest());

        assertEquals("Original", bond.getName());
        assertEquals(0, new BigDecimal("7.20").compareTo(bond.getCouponRate()));
        assertEquals(LocalDate.of(2030, 1, 1), bond.getMaturityDate());
        assertEquals(0, new BigDecimal("7.20").compareTo(bond.getAnnualYtm()));
    }

    @Test
    void shouldKeepYtmSuppliedInTheSameRequestAsAPriceChange() {

        Bond bond = existingBond();

        UpdateBondRequest request = new UpdateBondRequest();
        request.setPrice("101.00");
        request.setAnnualYtm("7.25%");

        mapper.applyUpdate(bond, request);

        assertEquals(0, new BigDecimal("101.00").compareTo(bond.getPrice()));
        assertEquals(0, new BigDecimal("7.25").compareTo(bond.getAnnualYtm()));
        assertNull(bond.getSemiYtm());
        assertNotNull(bond.getYtmCalculatedAt());
    }

    @Test
    void shouldClearYtmWhenOnlyPriceChanges() {

        Bond bond = existingBond();

        UpdateBondRequest request = new UpdateBondRequest();
        request.setPrice("101.00");

        mapper.applyUpdate(bond, request);

        assertNull(bond.getSemiYtm());
        assertNull(bond.getAnnualYtm());
        assertNull(bond.getYtc());
        assertNull(bond.getYtmCalculatedAt());
    }

    @Test
    void shouldNormalizeRawValuesOnUpdate() {

        Bond bond = existingBond();

        UpdateBondRequest request = new UpdateBondRequest();
        request.setCouponRate("9.10%");
        request.setSecurityType("Unsecured");
        request.setQuantumDescription("70 Lakh");
        request.setLotSizeDescription("775 Lot");
        request.setIpDateDescription("1st of Every Month");

        mapper.applyUpdate(bond, request);

        assertEquals(0, new BigDecimal("9.10").compareTo(bond.getCouponRate()));
        assertEquals(SecurityType.UNSECURED, bond.getSecurityType());
        assertEquals(0, new BigDecimal("70.00").compareTo(bond.getQuantumInLacs()));
        assertEquals(0, new BigDecimal("775").compareTo(bond.getLotSize()));
        assertEquals(CouponFrequency.MONTHLY, bond.getCouponFrequency());
    }

    @Test
    void shouldReDeriveMaturityFromANewDescription() {

        Bond bond = existingBond();

        UpdateBondRequest request = new UpdateBondRequest();
        request.setMaturityDescription("9/11/2024 to 9/11/2033 (10% each year)");

        mapper.applyUpdate(bond, request);

        assertEquals(LocalDate.of(2033, 11, 9), bond.getMaturityDate());
        assertEquals(MaturityType.RANGE, bond.getMaturityType());
    }

    @Test
    void shouldClearDateAndTypeWhenMaturityDescriptionIsCleared() {

        Bond bond = existingBond();

        UpdateBondRequest request = new UpdateBondRequest();
        request.setMaturityDescription("");

        mapper.applyUpdate(bond, request);

        assertNull(bond.getMaturityDescription());
        assertNull(bond.getMaturityDate());
        assertNull(bond.getMaturityType());
    }

    // =========================================================
    // FIXTURE
    // =========================================================

    private Bond existingBond() {

        return Bond.builder()
                .name("Original")
                .isin("INE000000009")
                .securityType(SecurityType.SECURED)
                .couponRate(new BigDecimal("7.20"))
                .couponFrequency(CouponFrequency.YEARLY)
                .maturityType(MaturityType.FIXED)
                .maturityDate(LocalDate.of(2030, 1, 1))
                .maturityDescription("1/Jan/30")
                .annualYtm(new BigDecimal("7.20"))
                .semiYtm(new BigDecimal("7.07"))
                .ytc(new BigDecimal("7.20"))
                .build();
    }
}
