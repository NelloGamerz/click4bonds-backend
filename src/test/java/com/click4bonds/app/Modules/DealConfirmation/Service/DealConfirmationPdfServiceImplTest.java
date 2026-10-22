package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

/**
 * The corporate letter's rendering: a real PDF, from the values alone.
 *
 * <p>The layout itself is reviewed by eye — a sample is written to
 * {@code target/pdf-inspect} for that purpose. What is asserted here is the
 * contract the pipeline depends on: bytes that are a PDF, every supported input
 * rendering without failing, and a refusal rather than a broken file when there
 * is nothing to draw.</p>
 */
class DealConfirmationPdfServiceImplTest {

    private final DealConfirmationPdfServiceImpl service =
            new DealConfirmationPdfServiceImpl(new DocumentProperties());

    @Test
    void rendersAPdfDocument() {

        byte[] pdf = service.render(values());

        assertTrue(pdf.length > 1000, "expected a real document, got " + pdf.length + " bytes");
        assertArrayEquals(
                "%PDF".getBytes(StandardCharsets.US_ASCII),
                java.util.Arrays.copyOf(pdf, 4));

        writeSample(pdf);
    }

    @Test
    void rendersALetterWithEveryFigureMissing() {

        /*
         * A deal may legitimately carry no coupon, no maturity and no accrual —
         * a perpetual bond, say. The letter prints an em dash where a figure is
         * absent rather than failing, because a confirmation with a blank is
         * still a confirmation.
         */
        DealConfirmationSheetValues sparse = new DealConfirmationSheetValues(
                LocalDate.of(2026, 10, 5),
                "DC-20261005-000002",
                "Test Customer",
                "Counterparty Name- Test Customer",
                "Our Sale",
                "ICCL",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        byte[] pdf = service.render(sparse);

        assertTrue(pdf.length > 1000);
    }

    @Test
    void refusesWhenThereAreNoValues() {

        assertThrows(DocumentGenerationException.class, () -> service.render(null));
    }

    private void writeSample(byte[] pdf) {

        try {

            Path outDir = Path.of("target", "pdf-inspect");
            Files.createDirectories(outDir);
            Files.write(outDir.resolve("deal-confirmation-sample.pdf"), pdf);

        } catch (Exception failure) {

            /*
             * Reviewing the sample is a convenience, not part of the contract;
             * a machine that cannot write target/ should not fail the test.
             */
            throw new IllegalStateException("could not write the sample", failure);
        }
    }

    private DealConfirmationSheetValues values() {

        return new DealConfirmationSheetValues(
                LocalDate.of(2026, 10, 5),
                "DC-20261005-000002",
                "Karan Pareek",
                "Counterparty Name- Karan Pareek",
                "Our Sale",
                "ICCL",
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 5),
                "INE0I5X07042",
                new BigDecimal("99.69"),
                "14.40% Monedo Fin Ser Pvt Ltd 2027",
                LocalDate.of(2027, 10, 1),
                "1st of every month",
                null,
                LocalDate.of(2026, 10, 1),
                new BigDecimal("14.40"),
                4L,
                79_520L,
                new BigDecimal("8000000.00"),
                new BigDecimal("7927202.88"),
                new BigDecimal("9909.68"),
                new BigDecimal("8.00"),
                new BigDecimal("7937120.56"),
                4L,
                new BigDecimal("12800.00"),
                new BigDecimal("7940002.88"),
                new BigDecimal("1280.00"),
                new BigDecimal("7938722.88"));
    }
}
