package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowResponse;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/** Temporary: writes sample statements to target/pdf-inspect for visual review. */
class PdfInspectionTest {

    @Test
    void writeSamples() throws Exception {

        Bond bond = new Bond();
        bond.setIsin("INE549K08590");
        bond.setName("MUTHOOT FINCORP LIMITED");
        bond.setRating("AA");
        bond.setRatingAgency("CRISIL");
        bond.setCouponRate(new BigDecimal("10.40"));
        bond.setCouponFrequency(CouponFrequency.MONTHLY);
        bond.setPrice(new BigDecimal("101.8766"));
        bond.setAnnualYtm(new BigDecimal("10.50"));
        bond.setMaturityDate(LocalDate.of(2033, 12, 30));
        bond.setMaturityType(MaturityType.FIXED);
        bond.setSecurityType(SecurityType.UNSECURED);
        bond.setCategory("Category 2 ( Corporate Bond )");
        bond.setIpDateDescription("31st of every month");

        BondCashFlowServiceImpl cashFlowService = new BondCashFlowServiceImpl(
                new CouponDateGenerator(),
                new PrincipalRepaymentServiceImpl(new MaturityDescriptionParserImpl()),
                new CouponCalculationServiceImpl(),
                new AccruedInterestServiceImpl(new CouponScheduleService(new CouponDateGenerator())));

        BondCashFlowResponse response = cashFlowService.generateSchedule(
                bond,
                LocalDate.of(2026, 10, 8),
                BigDecimal.ONE);

        BondCashFlowPdfServiceImpl pdfService = new BondCashFlowPdfServiceImpl();

        Path outDir = Path.of("target", "pdf-inspect");
        Files.createDirectories(outDir);
        Files.write(outDir.resolve("cashflow-sample.pdf"), pdfService.render(bond, response));
    }
}
