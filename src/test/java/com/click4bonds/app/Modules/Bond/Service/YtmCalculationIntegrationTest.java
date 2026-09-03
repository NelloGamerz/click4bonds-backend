package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;

/**
 * Integration test using real BondCashFlowService, CouponDateGenerator,
 * PrincipalRepaymentService, CouponCalculationService, and XirrCalculator.
 *
 * Only the BondRepository is mocked to avoid database dependencies.
 */
@ExtendWith(MockitoExtension.class)
class YtmCalculationIntegrationTest {

    private static final LocalDate CALCULATION_DATE = LocalDate.of(2026, 9, 3);

    @Mock
    private BondRepository bondRepository;

    @Test
    void calculatesYtmUsingRealServicesForStagedAmortizationBond() {
        System.out.println(
                "\n========== YTM INTEGRATION TEST ==========\n");

        // Create the full staged bond
        Bond bond = createFullStagedBond();

        System.out.println("Bond Price: " + bond.getPrice());
        System.out.println("Calculation Date: " + CALCULATION_DATE);
        System.out.println("Coupon Rate: " + bond.getCouponRate());
        System.out.println("Coupon Frequency: " + bond.getCouponFrequency());
        System.out.println("Maturity Date: " + bond.getMaturityDate());
        System.out.println("Maturity Type: " + bond.getMaturityType());
        System.out.println("IP Date Description: " + bond.getIpDateDescription());
        System.out.println("Maturity Description: " + bond.getMaturityDescription());

        // Use real service implementations
        BondCashFlowService bondCashFlowService = new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(
                        new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));

        XirrCalculator xirrCalculator = new XirrCalculator();

        when(bondRepository.save(any(Bond.class)))
                .thenReturn(bond);

        // Create YTM service with real dependencies
        YtmCalculationService ytmService = new YtmCalculationServiceImpl(
                bondCashFlowService,
                xirrCalculator,
                bondRepository);

        // Calculate YTM
        BigDecimal calculatedYtm = ytmService.calculateYtm(bond);

        // Print results
        System.out.println("\nCash Flow Count: " + 12);
        System.out.println("Calculated Annual YTM: " + calculatedYtm);
        System.out.println("Calculated Annual YTM (%): "
                + bond.getAnnualYtm());

        // Verify results
        assertNotNull(calculatedYtm);
        assertNotNull(bond.getAnnualYtm());
        assertNotNull(bond.getYtmCalculatedAt());

        // Dirty settlement pricing lowers the staged bond's XIRR.
        // For staged amortization, XIRR should be close to coupon rate but adjusted for price
        BigDecimal expectedXirrDecimal = new BigDecimal("0.100385");
        BigDecimal expectedYtmPercentage = new BigDecimal("10.04");

        // Check that the calculated XIRR is close to expected (within 0.01 tolerance)
        assertEquals(expectedYtmPercentage, bond.getAnnualYtm());

        // Verify the decimal form is also correct
        assertEquals(0, expectedXirrDecimal.compareTo(calculatedYtm));

        System.out.println(
                "\n========== INTEGRATION TEST PASSED ==========\n");
    }

    @Test
    void calculatesMuthootDirtyPriceCashFlowsWithRealXirr() {
        Bond bond = new Bond();
        bond.setPrice(new BigDecimal("99.63"));
        bond.setCouponRate(new BigDecimal("8.80"));
        bond.setCouponFrequency(CouponFrequency.YEARLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2028, 2, 2));
        bond.setIpDateDescription("02/02 Ann");

        BondCashFlowService service = new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));

        java.util.List<XirrCalculator.CashFlow> flows = service.generateCashFlows(bond, CALCULATION_DATE);
        assertEquals(java.util.List.of(
                new BigDecimal("-104.76534246575342465753"),
                new BigDecimal("8.8000000000"),
                new BigDecimal("108.8000000000")),
                flows.stream().map(XirrCalculator.CashFlow::amount).toList());

        BigDecimal xirr = new XirrCalculator().calculate(flows);
        assertTrue(new BigDecimal("0.090174").subtract(xirr).abs()
                .compareTo(new BigDecimal("0.000001")) <= 0, "Actual XIRR: " + xirr);
    }

    /**
     * Creates the full staged amortization bond used in tests.
     *
     * Configuration:
     * - Price: 94.50
     * - Coupon: 7.20%
     * - Frequency: HALF_YEARLY
     * - IP: 26/03-26/09
     * - Maturity: 2031-09-26
     * - Maturity Description: 26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)
     *
     * @return Bond with staged amortization schedule
     */
    private Bond createFullStagedBond() {
        Bond bond = new Bond();
        bond.setId(UUID.randomUUID());
        bond.setName("Test Bond - Staged Amortization");
        bond.setIsin("STAGE0000001");
        bond.setPrice(new BigDecimal("94.50"));
        bond.setCouponRate(new BigDecimal("7.20"));
        bond.setCouponFrequency(CouponFrequency.HALF_YEARLY);
        bond.setMaturityType(MaturityType.FIXED);
        bond.setMaturityDate(LocalDate.of(2031, 9, 26));
        bond.setIpDateDescription("26/03-26/09");
        bond.setMaturityDescription(
                "26-09-2031 (2.5% on Each IP till 2027 and 10% on Each IP from 2028 to 2031)");

        return bond;
    }
}
