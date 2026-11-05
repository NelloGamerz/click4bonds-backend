package com.click4bonds.app.Modules.Offer.Service.Design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Offer.Dto.OfferDetails;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

class DiwaliOfferDesignTest {

    @Test
    void rendersASinglePagePdfWithTheOfferContent() throws Exception {

        DiwaliOfferDesign design = new DiwaliOfferDesign(new DocumentProperties());

        OfferDetails offer = new OfferDetails(
                "INE123A01011",
                "Test Bond Limited 8.45% 2030",
                "Secured",
                "CRISIL AAA",
                new BigDecimal("8.45"),
                "Half yearly",
                LocalDate.of(2030, 3, 31),
                "NA",
                new BigDecimal("99.5000"),
                new BigDecimal("12.5000"),
                LocalDate.of(2026, 11, 5));

        byte[] pdf = design.render(offer);

        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertTrue(header.startsWith("%PDF"), "output is not a PDF");

        PdfReader reader = new PdfReader(pdf);

        try {
            assertEquals(1, reader.getNumberOfPages(), "the offer should fit one page");

            String text = new PdfTextExtractor(reader).getTextFromPage(1);

            assertTrue(text.contains("Click4Bonds"), "our company should head the offer");
            assertTrue(text.contains("Test Bond Limited 8.45% 2030"), "bond name missing");
            assertTrue(text.contains("OFFER PRICE"), "price figure missing");
            assertTrue(text.contains("YIELD TO MATURITY"), "yield figure missing");
            assertTrue(text.contains("99.50"), "price value missing");
            assertTrue(text.contains("12.50%"), "yield value missing");

        } finally {
            reader.close();
        }
    }
}
