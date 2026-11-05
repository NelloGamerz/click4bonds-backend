//package com.click4bonds.app.Modules.Offer.Service.Design;
//
//import java.awt.Color;
//import java.io.ByteArrayOutputStream;
//import java.math.BigDecimal;
//import java.text.DecimalFormat;
//import java.text.DecimalFormatSymbols;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.Locale;
//
//import org.springframework.stereotype.Component;
//
//import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
//import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;
//import com.click4bonds.app.Modules.Offer.Dto.OfferDetails;
//import com.click4bonds.app.Modules.Offer.Service.OfferDesignStrategy;
//import com.lowagie.text.Document;
//import com.lowagie.text.DocumentException;
//import com.lowagie.text.Element;
//import com.lowagie.text.Font;
//import com.lowagie.text.FontFactory;
//import com.lowagie.text.PageSize;
//import com.lowagie.text.Paragraph;
//import com.lowagie.text.Phrase;
//import com.lowagie.text.Rectangle;
//import com.lowagie.text.pdf.ColumnText;
//import com.lowagie.text.pdf.PdfContentByte;
//import com.lowagie.text.pdf.PdfPCell;
//import com.lowagie.text.pdf.PdfPTable;
//import com.lowagie.text.pdf.PdfPageEventHelper;
//import com.lowagie.text.pdf.PdfWriter;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
/// **
// * The Diwali offer letter.
// *
// * <p>
// * A warm festive design rather than the neutral deal-confirmation letter: a deep
// * maroon masthead with gold lettering carries our company first, then the bond
// * and the two figures the caller is being offered. It is drawn entirely with
// * OpenPDF's base-14 fonts and vector shapes, so it needs no asset and renders
// * identically everywhere.
// *
// * <p>
// * This class computes nothing — every figure arrives already resolved on
// * {@link OfferDetails}.
// */
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class DiwaliOfferDesign implements OfferDesignStrategy {
//
//    private static final String LAYOUT = "DIWALI";
//
//    private static final float MARGIN = 40f;
//
//    /** Height of the maroon masthead; the page's top margin reserves it. */
//    private static final float BAND_HEIGHT = 122f;
//
//    private static final Color MAROON = new Color(0x6E, 0x1B, 0x1B);
//    private static final Color MAROON_DEEP = new Color(0x4A, 0x10, 0x10);
//    private static final Color GOLD = new Color(0xC8, 0x8A, 0x24);
//    private static final Color GOLD_PALE = new Color(0xF2, 0xE1, 0xBE);
//    private static final Color CREAM = new Color(0xFF, 0xFB, 0xF2);
//    private static final Color CARD = new Color(0xFF, 0xFF, 0xFF);
//    private static final Color CARD_BORDER = new Color(0xEB, 0xD3, 0x9A);
//    private static final Color DIVIDER = new Color(0xF0, 0xE2, 0xC8);
//    private static final Color INK = new Color(0x33, 0x25, 0x1B);
//    private static final Color MUTED = new Color(0x7A, 0x6A, 0x58);
//    private static final Color WHITE = Color.WHITE;
//
//    private static final String NONE = "—";
//
//    private static final DateTimeFormatter DATE =
//            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
//
//    private static final ThreadLocal<DecimalFormat> PRICE = ThreadLocal.withInitial(
//            () -> new DecimalFormat("#,##0.00##", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));
//
//    private static final ThreadLocal<DecimalFormat> RATE = ThreadLocal.withInitial(
//            () -> new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));
//
//    private final DocumentProperties properties;
//
//    @Override
//    public String design() {
//        return LAYOUT;
//    }
//
//    @Override
//    public byte[] render(OfferDetails offer) {
//
//        if (offer == null) {
//            throw new DocumentGenerationException(
//                    "No offer values were supplied, so no offer can be drawn");
//        }
//
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//
//        Document document = new Document(
//                PageSize.A4, MARGIN, MARGIN, BAND_HEIGHT + 26f, 42f);
//
//        document.addTitle("Diwali Bond Offer - " + offer.isin());
//        document.addAuthor(properties.getOrganisation().getBeneficiaryName());
//        document.addCreator("CLICK4BOND");
//
//        try {
//
//            PdfWriter writer = PdfWriter.getInstance(document, out);
//
//            writer.setPageEvent(new FestivePage());
//
//            document.open();
//
//            addGreeting(document);
//            addOfferHeadline(document, offer);
//            addFigures(document, offer);
//            addBondDetails(document, offer);
//            addTerms(document, offer);
//
//            document.close();
//
//        } catch (DocumentException failure) {
//
//            log.error("Failed to draw the Diwali offer PDF for isin={}", offer.isin(), failure);
//
//            throw new DocumentGenerationException(
//                    "Could not generate the offer PDF for " + offer.isin(), failure);
//
//        } finally {
//
//            if (document.isOpen()) {
//                document.close();
//            }
//        }
//
//        return out.toByteArray();
//    }
//
//    // =========================================================
//    // SECTIONS
//    // =========================================================
//
//    private void addGreeting(Document document) throws DocumentException {
//
//        Paragraph greeting = new Paragraph(
//                "Wishing you light, prosperity and confident investing this festive season.",
//                regular(10f, MUTED));
//        greeting.setAlignment(Element.ALIGN_CENTER);
//        greeting.setSpacingBefore(6f);
//        greeting.setSpacingAfter(2f);
//
//        document.add(greeting);
//
//        Paragraph eyebrow = new Paragraph("AN EXCLUSIVE BOND OFFER", bold(8f, GOLD));
//        eyebrow.setAlignment(Element.ALIGN_CENTER);
//        eyebrow.setSpacingBefore(14f);
//        eyebrow.setSpacingAfter(0f);
//
//        document.add(eyebrow);
//    }
//
//    private void addOfferHeadline(Document document, OfferDetails offer)
//            throws DocumentException {
//
//        Paragraph name = new Paragraph(text(offer.bondName()), bold(17f, MAROON_DEEP));
//        name.setAlignment(Element.ALIGN_CENTER);
//        name.setSpacingBefore(5f);
//        name.setSpacingAfter(1f);
//
//        document.add(name);
//
//        Paragraph isin = new Paragraph(
//                "ISIN " + text(offer.isin()), regular(9f, MUTED));
//        isin.setAlignment(Element.ALIGN_CENTER);
//        isin.setSpacingAfter(12f);
//
//        document.add(isin);
//    }
//
//    private void addFigures(Document document, OfferDetails offer) throws DocumentException {
//
//        PdfPTable figures = new PdfPTable(new float[] { 50f, 50f });
//        figures.setWidthPercentage(100f);
//
//        figures.addCell(figureCell(
//                "OFFER PRICE",
//                priceText(offer.price()),
//                "per 100 of face value"));
//
//        figures.addCell(figureCell(
//                "YIELD TO MATURITY",
//                percentText(offer.ytmPercentage()),
//                "annualised"));
//
//        document.add(card(figures));
//
//        Paragraph asOf = new Paragraph(
//                "As on " + date(offer.asOf()), regular(8.5f, MUTED));
//        asOf.setAlignment(Element.ALIGN_CENTER);
//        asOf.setSpacingBefore(5f);
//        asOf.setSpacingAfter(10f);
//
//        document.add(asOf);
//    }
//
//    private PdfPCell figureCell(String label, String value, String caption) {
//
//        PdfPTable block = new PdfPTable(1);
//        block.setWidthPercentage(100f);
//
//        Paragraph heading = new Paragraph(label, bold(7.5f, GOLD));
//        heading.setAlignment(Element.ALIGN_CENTER);
//        heading.setSpacingBefore(4f);
//        heading.setSpacingAfter(2f);
//        block.addCell(noBorder(heading));
//
//        Paragraph figure = new Paragraph(value, bold(23f, MAROON));
//        figure.setAlignment(Element.ALIGN_CENTER);
//        figure.setSpacingBefore(0f);
//        figure.setSpacingAfter(1f);
//        block.addCell(noBorder(figure));
//
//        Paragraph note = new Paragraph(caption, regular(7.5f, MUTED));
//        note.setAlignment(Element.ALIGN_CENTER);
//        note.setSpacingAfter(4f);
//        block.addCell(noBorder(note));
//
//        PdfPCell cell = new PdfPCell(block);
//        cell.setBorder(Rectangle.NO_BORDER);
//        cell.setPadding(8f);
//        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
//
//        return cell;
//    }
//
//    private void addBondDetails(Document document, OfferDetails offer)
//            throws DocumentException {
//
//        PdfPTable rows = new PdfPTable(new float[] { 40f, 60f });
//        rows.setWidthPercentage(100f);
//
//        addRow(rows, "Security type", text(offer.securityType()));
//        addRow(rows, "Coupon rate", percentText(offer.couponRate()));
//        addRow(rows, "Interest payment", text(offer.couponFrequency()));
//        addRow(rows, "Maturity date", date(offer.maturityDate()));
//        addRow(rows, "Rating", text(offer.rating()));
//        addRow(rows, "Put / Call",
//                offer.putCallDescription() == null || offer.putCallDescription().isBlank()
//                        ? "N.A"
//                        : offer.putCallDescription());
//
//        PdfPTable heading = new PdfPTable(1);
//        heading.setWidthPercentage(100f);
//
//        Paragraph title = new Paragraph("ABOUT THE BOND", bold(8f, MAROON));
//        title.setSpacingAfter(3f);
//        heading.addCell(noBorder(title));
//        heading.addCell(noBorder(rows));
//
//        document.add(card(heading));
//    }
//
//    private void addTerms(Document document, OfferDetails offer) throws DocumentException {
//
//        Paragraph terms = new Paragraph(
//                "This offer is valid as on " + date(offer.asOf())
//                        + ". Price and yield are indicative and subject to market movement. "
//                        + "Please read the related offer document and rating rationale before investing.",
//                regular(7.8f, MUTED));
//        terms.setAlignment(Element.ALIGN_JUSTIFIED);
//        terms.setLeading(10.5f);
//        terms.setSpacingBefore(12f);
//
//        document.add(terms);
//
//        Paragraph wishes = new Paragraph(
//                "With warm Diwali wishes,", regular(9.5f, MAROON_DEEP));
//        wishes.setSpacingBefore(12f);
//        wishes.setSpacingAfter(1f);
//
//        Paragraph team = new Paragraph("Team Click4Bonds", bold(10f, MAROON));
//        team.setSpacingAfter(0f);
//
//        document.add(wishes);
//        document.add(team);
//    }
//
//    // =========================================================
//    // BUILDING BLOCKS
//    // =========================================================
//
//    private void addRow(PdfPTable table, String label, String value) {
//
//        PdfPCell key = new PdfPCell(new Phrase(label, regular(9f, MUTED)));
//        key.setBorder(Rectangle.BOTTOM);
//        key.setBorderColor(DIVIDER);
//        key.setBorderWidth(0.6f);
//        key.setPaddingTop(5f);
//        key.setPaddingBottom(5f);
//        key.setPaddingLeft(0f);
//
//        PdfPCell figure = new PdfPCell(new Phrase(value, bold(9.4f, INK)));
//        figure.setBorder(Rectangle.BOTTOM);
//        figure.setBorderColor(DIVIDER);
//        figure.setBorderWidth(0.6f);
//        figure.setPaddingTop(5f);
//        figure.setPaddingBottom(5f);
//        figure.setHorizontalAlignment(Element.ALIGN_RIGHT);
//
//        table.addCell(key);
//        table.addCell(figure);
//    }
//
//    /** Wraps content in the white, gold-bordered card the offer uses. */
//    private PdfPTable card(PdfPTable content) {
//
//        PdfPTable card = new PdfPTable(1);
//        card.setWidthPercentage(100f);
//        card.setSpacingBefore(2f);
//        card.setSpacingAfter(2f);
//
//        PdfPCell cell = new PdfPCell(content);
//        cell.setBackgroundColor(CARD);
//        cell.setBorder(Rectangle.BOX);
//        cell.setBorderColor(CARD_BORDER);
//        cell.setBorderWidth(1f);
//        cell.setPadding(12f);
//
//        card.addCell(cell);
//
//        return card;
//    }
//
//    private PdfPCell noBorder(Element content) {
//
//        PdfPCell cell = new PdfPCell();
//        cell.setBorder(Rectangle.NO_BORDER);
//        cell.setPadding(0f);
//        cell.addElement(content);
//
//        return cell;
//    }
//
//    // =========================================================
//    // FORMATTING
//    // =========================================================
//
//    private static Font regular(float size, Color color) {
//        return FontFactory.getFont(FontFactory.HELVETICA, size, Font.NORMAL, color);
//    }
//
//    private static Font bold(float size, Color color) {
//        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, Font.BOLD, color);
//    }
//
//    private static String text(String value) {
//        return value == null || value.isBlank() ? NONE : value;
//    }
//
//    private static String date(LocalDate value) {
//        return value == null ? NONE : value.format(DATE);
//    }
//
//    private static String priceText(BigDecimal value) {
//        return value == null ? NONE : PRICE.get().format(value);
//    }
//
//    private static String percentText(BigDecimal value) {
//        return value == null ? NONE : RATE.get().format(value) + "%";
//    }
//
//    // =========================================================
//    // PAGE FURNITURE
//    // =========================================================
//
//    /**
//     * Paints the cream page, the maroon and gold masthead, and a pale rangoli in
//     * the foot corners. The masthead belongs to the page so a second page would
//     * carry it too; the page's top margin reserves its height so body text never
//     * reaches it.
//     */
//    private final class FestivePage extends PdfPageEventHelper {
//
//        @Override
//        public void onEndPage(PdfWriter writer, Document document) {
//
//            Rectangle page = document.getPageSize();
//
//            float width = page.getWidth();
//            float height = page.getHeight();
//
//            PdfContentByte under = writer.getDirectContentUnder();
//
//            under.setColorFill(CREAM);
//            under.rectangle(0f, 0f, width, height);
//            under.fill();
//
//            drawRangoli(under, page);
//
//            PdfContentByte canvas = writer.getDirectContent();
//
//            canvas.setColorFill(MAROON);
//            canvas.rectangle(0f, height - BAND_HEIGHT, width, BAND_HEIGHT);
//            canvas.fill();
//
//            canvas.setColorFill(MAROON_DEEP);
//            canvas.rectangle(0f, height - BAND_HEIGHT, width, 26f);
//            canvas.fill();
//
//            canvas.setColorFill(GOLD);
//            canvas.rectangle(0f, height - BAND_HEIGHT, width, 2.5f);
//            canvas.fill();
//
//            drawMasthead(canvas, page);
//        }
//
//        private void drawMasthead(PdfContentByte canvas, Rectangle page) {
//
//            float width = page.getWidth();
//            float height = page.getHeight();
//
//            ColumnText.showTextAligned(
//                    canvas, Element.ALIGN_CENTER,
//                    new Phrase(
//                            text(properties.getOrganisation().getBeneficiaryName())
//                                    .toUpperCase(Locale.ENGLISH),
//                            bold(8f, GOLD_PALE)),
//                    width / 2f, height - 22f, 0f);
//
//            ColumnText.showTextAligned(
//                    canvas, Element.ALIGN_CENTER,
//                    new Phrase("Click4Bonds", bold(27f, GOLD)),
//                    width / 2f, height - 60f, 0f);
//
//            ColumnText.showTextAligned(
//                    canvas, Element.ALIGN_CENTER,
//                    new Phrase("DIWALI SPECIAL OFFER", bold(10f, WHITE)),
//                    width / 2f, height - 88f, 0f);
//
//            /*
//             * Three small gold lamps under the title — a simple festive mark,
//             * drawn as circles so the PDF needs no image and no glyph the
//             * base-14 fonts might lack.
//             */
//            canvas.setColorFill(GOLD);
//
//            for (int lamp = -1; lamp <= 1; lamp++) {
//
//                canvas.circle(width / 2f + lamp * 14f, height - 105f, 2.4f);
//                canvas.fill();
//            }
//        }
//
//        /** Concentric rings and dots in the two foot corners, in pale gold. */
//        private void drawRangoli(PdfContentByte canvas, Rectangle page) {
//
//            float width = page.getWidth();
//
//            canvas.setColorStroke(GOLD_PALE);
//            canvas.setColorFill(GOLD_PALE);
//            canvas.setLineWidth(0.8f);
//
//            for (float radius = 26f; radius <= 120f; radius += 15f) {
//
//                canvas.circle(-8f, -8f, radius);
//                canvas.stroke();
//
//                canvas.circle(width + 8f, -8f, radius);
//                canvas.stroke();
//            }
//
//            for (float radius = 34f; radius <= 112f; radius += 30f) {
//
//                for (int dot = 0; dot < 8; dot++) {
//
//                    double angle = Math.PI / 4 * dot;
//
//                    float x = (float) Math.cos(angle) * radius;
//                    float y = (float) Math.sin(angle) * radius;
//
//                    canvas.circle(-8f + x, -8f + y, 1.6f);
//                    canvas.fill();
//
//                    canvas.circle(width + 8f - x, -8f + y, 1.6f);
//                    canvas.fill();
//                }
//            }
//        }
//    }
//}


package com.click4bonds.app.Modules.Offer.Service.Design;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;
import com.click4bonds.app.Modules.Offer.Dto.OfferDetails;
import com.click4bonds.app.Modules.Offer.Service.OfferDesignStrategy;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The Diwali offer letter.
 *
 * <p>
 * A festive letter: a maroon masthead with a gold diya, a hanging toran of
 * gold and maroon pennants, rangoli mandalas in the lower corners, a maroon
 * "figures" card carrying the offer price and yield, and a grid of bond tiles.
 * Everything is vector art on OpenPDF's base-14 fonts, so it needs no asset and
 * renders identically everywhere.
 *
 * <p>
 * This class computes nothing — every figure arrives already resolved on
 * {@link OfferDetails}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiwaliOfferDesign implements OfferDesignStrategy {

    private static final String LAYOUT = "DIWALI";

    private static final float MARGIN = 48f;

    /** Height of the maroon masthead. */
    private static final float BAND_HEIGHT = 146f;

    /** Height of the gold rule under the masthead. */
    private static final float RULE_HEIGHT = 3f;

    /** Depth of the hanging pennants under the rule. */
    private static final float TORAN_DEPTH = 16f;

    /** Height of the maroon footer strip. */
    private static final float FOOTER_HEIGHT = 28f;

    private static final int TORAN_COUNT = 25;

    private static final Color MAROON = new Color(0x6E, 0x1B, 0x1B);
    private static final Color MAROON_DEEP = new Color(0x4A, 0x10, 0x10);
    private static final Color MAROON_LIGHT = new Color(0x8E, 0x2B, 0x2B);
    private static final Color GOLD = new Color(0xC8, 0x8A, 0x24);
    private static final Color GOLD_BRIGHT = new Color(0xF4, 0xC6, 0x5A);
    private static final Color GOLD_PALE = new Color(0xF2, 0xE1, 0xBE);
    private static final Color FLAME = new Color(0xF4, 0xA6, 0x2A);
    private static final Color FLAME_CORE = new Color(0xFF, 0xF1, 0xC2);
    private static final Color CREAM = new Color(0xFF, 0xFB, 0xF2);
    private static final Color HIGHLIGHT = new Color(0xFF, 0xF1, 0xCE);
    private static final Color CARD = Color.WHITE;
    private static final Color CARD_BORDER = new Color(0xEB, 0xD3, 0x9A);
    private static final Color INK = new Color(0x33, 0x25, 0x1B);
    private static final Color MUTED = new Color(0x7A, 0x6A, 0x58);
    private static final Color WHITE = Color.WHITE;

    private static final String NONE = "—";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final ThreadLocal<DecimalFormat> PRICE = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.00##", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    private static final ThreadLocal<DecimalFormat> RATE = ThreadLocal.withInitial(
            () -> new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    private final DocumentProperties properties;

    @Override
    public String design() {
        return LAYOUT;
    }

    @Override
    public byte[] render(OfferDetails offer) {

        if (offer == null) {
            throw new DocumentGenerationException(
                    "No offer values were supplied, so no offer can be drawn");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(
                PageSize.A4, MARGIN, MARGIN,
                BAND_HEIGHT + RULE_HEIGHT + TORAN_DEPTH + 22f,
                FOOTER_HEIGHT + 28f);

        document.addTitle("Diwali Bond Offer - " + offer.isin());
        document.addAuthor(properties.getOrganisation().getBeneficiaryName());
        document.addCreator("CLICK4BOND");

        try {

            PdfWriter writer = PdfWriter.getInstance(document, out);

            writer.setPageEvent(new FestivePage());

            document.open();

            addGreeting(document);
            addOfferHeadline(document, offer);
            addFigures(document, offer);
            addBondDetails(document, offer);
            addTerms(document, offer);

            document.close();

        } catch (DocumentException failure) {

            log.error("Failed to draw the Diwali offer PDF for isin={}", offer.isin(), failure);

            throw new DocumentGenerationException(
                    "Could not generate the offer PDF for " + offer.isin(), failure);

        } finally {

            if (document.isOpen()) {
                document.close();
            }
        }

        return out.toByteArray();
    }

    // =========================================================
    // SECTIONS
    // =========================================================

    private void addGreeting(Document document) throws DocumentException {

        Paragraph greeting = new Paragraph(
                "Wishing you light, prosperity and confident investing this festive season.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10f, Font.ITALIC, MUTED));
        greeting.setAlignment(Element.ALIGN_CENTER);
        greeting.setSpacingAfter(14f);

        document.add(greeting);
    }

    /**
     * The highlighted bond plaque: a pale-gold panel with the bond name in large
     * serif type, joined to a maroon strip that carries the ISIN.
     */
    private void addOfferHeadline(Document document, OfferDetails offer)
            throws DocumentException {

        Paragraph eyebrow = new Paragraph("AN EXCLUSIVE BOND OFFER", bold(8.5f, GOLD));
        eyebrow.setAlignment(Element.ALIGN_CENTER);
        eyebrow.setSpacingAfter(5f);

        Paragraph name = new Paragraph(text(offer.bondName()), serifBold(22f, MAROON_DEEP));
        name.setAlignment(Element.ALIGN_CENTER);

        PdfPCell plaque = new PdfPCell();
        plaque.addElement(eyebrow);
        plaque.addElement(name);
        plaque.setBackgroundColor(HIGHLIGHT);
        plaque.setBorder(Rectangle.BOX);
        plaque.setBorderColor(GOLD);
        plaque.setBorderWidth(1.2f);
        plaque.setPaddingTop(12f);
        plaque.setPaddingBottom(14f);
        plaque.setPaddingLeft(12f);
        plaque.setPaddingRight(12f);

        Paragraph isin = new Paragraph();
        isin.setAlignment(Element.ALIGN_CENTER);
        isin.add(new Chunk("ISIN   ", bold(8f, GOLD_PALE)));
        isin.add(new Chunk(text(offer.isin()), bold(13f, GOLD_BRIGHT)));

        PdfPCell strip = new PdfPCell(isin);
        strip.setBackgroundColor(MAROON);
        strip.setBorder(Rectangle.BOX);
        strip.setBorderColor(GOLD);
        strip.setBorderWidth(1.2f);
        strip.setPaddingTop(6f);
        strip.setPaddingBottom(8f);
        strip.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPTable headline = new PdfPTable(1);
        headline.setWidthPercentage(100f);
        headline.setSpacingAfter(14f);
        headline.addCell(plaque);
        headline.addCell(strip);

        document.add(headline);
    }

    /** The maroon card carrying the two figures the caller is being offered. */
    private void addFigures(Document document, OfferDetails offer) throws DocumentException {

        PdfPTable figures = new PdfPTable(new float[]{50f, 50f});
        figures.setWidthPercentage(100f);

        figures.addCell(figureCell(
                "OFFER PRICE", priceText(offer.price()), "per 100 of face value"));

        figures.addCell(figureCell(
                "YIELD TO MATURITY", percentText(offer.ytmPercentage()), "annualised"));

        document.add(figures);

        Paragraph asOf = new Paragraph("As on " + date(offer.asOf()), regular(8.5f, MUTED));
        asOf.setAlignment(Element.ALIGN_CENTER);
        asOf.setSpacingBefore(6f);
        asOf.setSpacingAfter(12f);

        document.add(asOf);
    }

    private PdfPCell figureCell(String label, String value, String caption) {

        Paragraph heading = new Paragraph(label, bold(8f, GOLD_PALE));
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingAfter(4f);

        Paragraph figure = new Paragraph(value, serifBold(30f, GOLD_BRIGHT));
        figure.setAlignment(Element.ALIGN_CENTER);
        figure.setSpacingAfter(2f);

        Paragraph note = new Paragraph(caption, regular(8f, GOLD_PALE));
        note.setAlignment(Element.ALIGN_CENTER);

        PdfPCell cell = new PdfPCell();
        cell.addElement(heading);
        cell.addElement(figure);
        cell.addElement(note);
        cell.setBackgroundColor(MAROON);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(GOLD);
        cell.setBorderWidth(1.2f);
        cell.setPaddingTop(16f);
        cell.setPaddingBottom(16f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        return cell;
    }

    private void addBondDetails(Document document, OfferDetails offer)
            throws DocumentException {

        Paragraph title = new Paragraph("ABOUT THE BOND", bold(9f, MAROON));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10f);

        document.add(title);

        String putCall = offer.putCallDescription() == null || offer.putCallDescription().isBlank()
                ? "N.A"
                : offer.putCallDescription();

        PdfPTable grid = new PdfPTable(new float[]{32f, 2f, 32f, 2f, 32f});
        grid.setWidthPercentage(100f);

        addTileRow(grid,
                tile("Security type", text(offer.securityType())),
                tile("Coupon rate", percentText(offer.couponRate())),
                tile("Interest payment", text(offer.couponFrequency())));

        for (int i = 0; i < 5; i++) {
            grid.addCell(spacer(10f));
        }

        addTileRow(grid,
                tile("Maturity date", date(offer.maturityDate())),
                tile("Rating", text(offer.rating())),
                tile("Put / Call", putCall));

        document.add(grid);
    }

    private void addTerms(Document document, OfferDetails offer) throws DocumentException {

        Paragraph terms = new Paragraph(
                "This offer is valid as on " + date(offer.asOf())
                        + ". Price and yield are indicative and subject to market movement. "
                        + "Please read the related offer document and rating rationale before investing.",
                regular(8f, MUTED));
        terms.setAlignment(Element.ALIGN_JUSTIFIED);
        terms.setLeading(11f);
        terms.setSpacingBefore(16f);

        document.add(terms);

        Paragraph wishes = new Paragraph(
                "With warm Diwali wishes,",
                FontFactory.getFont(FontFactory.TIMES_ITALIC, 10f, Font.ITALIC, MAROON_DEEP));
        wishes.setSpacingBefore(14f);
        wishes.setSpacingAfter(2f);

        Paragraph team = new Paragraph("Team Click4Bonds", bold(10.5f, MAROON));

        document.add(wishes);
        document.add(team);
    }

    // =========================================================
    // BUILDING BLOCKS
    // =========================================================

    private void addTileRow(PdfPTable grid, PdfPCell left, PdfPCell middle, PdfPCell right) {

        grid.addCell(left);
        grid.addCell(spacer(0f));
        grid.addCell(middle);
        grid.addCell(spacer(0f));
        grid.addCell(right);
    }

    /** A white, gold-bordered tile: small gold label over a bold value. */
    private PdfPCell tile(String label, String value) {

        Paragraph key = new Paragraph(label.toUpperCase(Locale.ENGLISH), bold(7f, GOLD));
        key.setAlignment(Element.ALIGN_CENTER);
        key.setSpacingAfter(4f);

        Paragraph figure = new Paragraph(value, bold(10.5f, INK));
        figure.setAlignment(Element.ALIGN_CENTER);

        PdfPCell cell = new PdfPCell();
        cell.addElement(key);
        cell.addElement(figure);
        cell.setBackgroundColor(CARD);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(CARD_BORDER);
        cell.setBorderWidth(0.8f);
        cell.setPadding(9f);
        cell.setMinimumHeight(50f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        return cell;
    }

    private PdfPCell spacer(float height) {

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);

        if (height > 0f) {
            cell.setFixedHeight(height);
        }

        return cell;
    }

    // =========================================================
    // FORMATTING
    // =========================================================

    private static Font regular(float size, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, Font.NORMAL, color);
    }

    private static Font bold(float size, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, Font.BOLD, color);
    }

    private static Font serifBold(float size, Color color) {
        return FontFactory.getFont(FontFactory.TIMES_BOLD, size, Font.BOLD, color);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }

    private static String date(LocalDate value) {
        return value == null ? NONE : value.format(DATE);
    }

    private static String priceText(BigDecimal value) {
        return value == null ? NONE : PRICE.get().format(value);
    }

    private static String percentText(BigDecimal value) {
        return value == null ? NONE : RATE.get().format(value) + "%";
    }

    // =========================================================
    // PAGE FURNITURE
    // =========================================================

    /**
     * Paints the cream page, rangoli corners and gold frame underneath the text,
     * then the masthead, toran and footer strip on top. The page's margins
     * reserve room for all of it, so body text never reaches it.
     */
    private final class FestivePage extends PdfPageEventHelper {

        @Override
        public void onEndPage(PdfWriter writer, Document document) {

            Rectangle page = document.getPageSize();

            float width = page.getWidth();
            float height = page.getHeight();

            PdfContentByte under = writer.getDirectContentUnder();

            under.setColorFill(CREAM);
            under.rectangle(0f, 0f, width, height);
            under.fill();

            drawRangoli(under, 0f, 0f);
            drawRangoli(under, width, 0f);
            drawFrame(under, width, height);

            PdfContentByte canvas = writer.getDirectContent();

            drawMasthead(canvas, width, height);
            drawToran(canvas, width, height);
            drawFooter(canvas, width);
        }

        // ---------- masthead ----------

        private void drawMasthead(PdfContentByte canvas, float width, float height) {

            float mid = width / 2f;

            canvas.setColorFill(MAROON);
            canvas.rectangle(0f, height - BAND_HEIGHT, width, BAND_HEIGHT);
            canvas.fill();

            canvas.setColorFill(GOLD);
            canvas.rectangle(0f, height - BAND_HEIGHT - RULE_HEIGHT, width, RULE_HEIGHT);
            canvas.fill();

            // Concentric gold rings tucked into both ends of the band.
            canvas.setColorStroke(GOLD);
            canvas.setLineWidth(0.8f);

            for (float radius = 30f; radius <= 62f; radius += 16f) {

                canvas.circle(0f, height - 73f, radius);
                canvas.stroke();

                canvas.circle(width, height - 73f, radius);
                canvas.stroke();
            }

            sparkle(canvas, 150f, height - 44f, 5f);
            sparkle(canvas, 445f, height - 50f, 4f);
            sparkle(canvas, 190f, height - 112f, 3f);
            sparkle(canvas, 405f, height - 108f, 3.5f);
            sparkle(canvas, 120f, height - 90f, 3f);
            sparkle(canvas, 475f, height - 86f, 4.5f);

            spaced(canvas,
                    text(properties.getOrganisation().getBeneficiaryName()).toUpperCase(Locale.ENGLISH),
                    mid, height - 24f, 8f, GOLD_PALE, 2f);

            drawDiya(canvas, mid, height - 76f);

            ColumnText.showTextAligned(
                    canvas, Element.ALIGN_CENTER,
                    new Phrase("Click4Bonds", serifBold(28f, GOLD_BRIGHT)),
                    mid, height - 118f, 0f);

            spaced(canvas, "DIWALI SPECIAL OFFER", mid, height - 136f, 9.5f, WHITE, 3f);
        }

        /** A diya: halo rings, gold bowl and rim, and a two-tone flame. */
        private void drawDiya(PdfContentByte canvas, float cx, float cy) {

            canvas.setColorStroke(MAROON_LIGHT);
            canvas.setLineWidth(0.5f);
            canvas.circle(cx, cy + 10f, 30f);
            canvas.stroke();
            canvas.circle(cx, cy + 10f, 38f);
            canvas.stroke();

            canvas.setColorFill(GOLD);
            canvas.moveTo(cx - 22f, cy);
            canvas.curveTo(cx - 20f, cy - 14f, cx + 20f, cy - 14f, cx + 22f, cy);
            canvas.closePath();
            canvas.fill();

            canvas.roundRectangle(cx - 8f, cy - 14f, 16f, 4f, 1.5f);
            canvas.fill();

            canvas.setColorFill(GOLD_BRIGHT);
            canvas.rectangle(cx - 24f, cy, 48f, 2.2f);
            canvas.fill();

            canvas.setColorFill(FLAME);
            canvas.moveTo(cx, cy + 26f);
            canvas.curveTo(cx + 10f, cy + 10f, cx + 7f, cy + 2f, cx, cy + 1f);
            canvas.curveTo(cx - 7f, cy + 2f, cx - 10f, cy + 10f, cx, cy + 26f);
            canvas.closePath();
            canvas.fill();

            canvas.setColorFill(FLAME_CORE);
            canvas.moveTo(cx, cy + 15f);
            canvas.curveTo(cx + 4.5f, cy + 8f, cx + 3.5f, cy + 3f, cx, cy + 2.5f);
            canvas.curveTo(cx - 3.5f, cy + 3f, cx - 4.5f, cy + 8f, cx, cy + 15f);
            canvas.closePath();
            canvas.fill();
        }

        /** Alternating gold and maroon pennants hanging under the gold rule. */
        private void drawToran(PdfContentByte canvas, float width, float height) {

            float step = width / TORAN_COUNT;
            float top = height - BAND_HEIGHT - RULE_HEIGHT;

            for (int i = 0; i < TORAN_COUNT; i++) {

                canvas.setColorFill(i % 2 == 0 ? GOLD : MAROON);
                canvas.moveTo(i * step, top);
                canvas.lineTo((i + 1) * step, top);
                canvas.lineTo((i + 0.5f) * step, top - TORAN_DEPTH);
                canvas.closePath();
                canvas.fill();
            }
        }

        // ---------- frame, footer ----------

        private void drawFrame(PdfContentByte canvas, float width, float height) {

            float top = height - BAND_HEIGHT - RULE_HEIGHT;

            canvas.setColorStroke(GOLD);
            canvas.setLineWidth(1.2f);
            canvas.rectangle(14f, 40f, width - 28f, top - 40f);
            canvas.stroke();

            canvas.setColorStroke(CARD_BORDER);
            canvas.setLineWidth(0.5f);
            canvas.rectangle(19f, 45f, width - 38f, top - 8f - 45f);
            canvas.stroke();
        }

        private void drawFooter(PdfContentByte canvas, float width) {

            canvas.setColorFill(GOLD);
            canvas.rectangle(0f, FOOTER_HEIGHT, width, 2f);
            canvas.fill();

            canvas.setColorFill(MAROON);
            canvas.rectangle(0f, 0f, width, FOOTER_HEIGHT);
            canvas.fill();

            spaced(canvas,
                    text(properties.getOrganisation().getBeneficiaryName()).toUpperCase(Locale.ENGLISH)
                            + "  |  HAPPY DIWALI",
                    width / 2f, 11f, 7.5f, GOLD_PALE, 2f);
        }

        // ---------- rangoli ----------

        /** A quarter-visible mandala: pale rings with two rings of petals. */
        private void drawRangoli(PdfContentByte canvas, float cx, float cy) {

            canvas.setColorStroke(CARD_BORDER);
            canvas.setColorFill(GOLD_PALE);
            canvas.setLineWidth(0.8f);

            for (float radius : new float[]{28f, 74f, 114f, 124f}) {

                canvas.circle(cx, cy, radius);
                canvas.stroke();
            }

            canvas.setLineWidth(0.6f);

            for (int i = 0; i < 12; i++) {

                petal(canvas, cx, cy, Math.toRadians(i * 30.0), 36f, 70f, 7f);
                petal(canvas, cx, cy, Math.toRadians(i * 30.0 + 15.0), 78f, 108f, 6f);
            }
        }

        /** One leaf-shaped petal from radius r0 to r1 along the given angle. */
        private void petal(PdfContentByte canvas, float cx, float cy,
                           double angle, float r0, float r1, float half) {

            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            float px = -dy;
            float py = dx;
            float len = r1 - r0;

            float bx = cx + dx * r0;
            float by = cy + dy * r0;
            float tx = cx + dx * r1;
            float ty = cy + dy * r1;

            canvas.moveTo(bx, by);
            canvas.curveTo(
                    bx + dx * len * 0.3f + px * half * 1.3f, by + dy * len * 0.3f + py * half * 1.3f,
                    tx - dx * len * 0.3f + px * half * 0.8f, ty - dy * len * 0.3f + py * half * 0.8f,
                    tx, ty);
            canvas.curveTo(
                    tx - dx * len * 0.3f - px * half * 0.8f, ty - dy * len * 0.3f - py * half * 0.8f,
                    bx + dx * len * 0.3f - px * half * 1.3f, by + dy * len * 0.3f - py * half * 1.3f,
                    bx, by);
            canvas.closePath();
            canvas.fillStroke();
        }

        /** A four-point star, drawn as a polygon so no glyph is needed. */
        private void sparkle(PdfContentByte canvas, float x, float y, float r) {

            float k = r * 0.22f;

            canvas.setColorFill(GOLD_PALE);
            canvas.moveTo(x, y + r);
            canvas.lineTo(x + k, y + k);
            canvas.lineTo(x + r, y);
            canvas.lineTo(x + k, y - k);
            canvas.lineTo(x, y - r);
            canvas.lineTo(x - k, y - k);
            canvas.lineTo(x - r, y);
            canvas.lineTo(x - k, y + k);
            canvas.closePath();
            canvas.fill();
        }

        /** Centred bold Helvetica with letter-spacing, which Phrase cannot do. */
        private void spaced(PdfContentByte canvas, String value, float x, float y,
                            float size, Color color, float spacing) {

            try {

                BaseFont base = BaseFont.createFont(
                        BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

                canvas.beginText();
                canvas.setFontAndSize(base, size);
                canvas.setColorFill(color);
                canvas.setCharacterSpacing(spacing);
                canvas.showTextAligned(PdfContentByte.ALIGN_CENTER, value, x, y, 0f);
                canvas.setCharacterSpacing(0f);
                canvas.endText();

            } catch (DocumentException | IOException failure) {

                throw new DocumentGenerationException(
                        "Could not draw the offer letter's lettering", failure);
            }
        }
    }
}