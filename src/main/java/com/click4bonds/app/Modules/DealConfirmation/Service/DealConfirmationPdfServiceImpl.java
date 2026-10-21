package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Draws the corporate deal confirmation letter as a PDF.
 *
 * <p>The layout follows {@code deal-confirmation.html} — the letterhead image,
 * the counterparty block, bond details beside deal terms, our particulars and
 * the customer's, the disclaimer, the signature block and the address bar — so
 * the PDF and the reference design read the same. It is drawn with OpenPDF row
 * by row rather than converted from the HTML, exactly as
 * {@code BondCashFlowPdfServiceImpl} draws the cash-flow statement, because this
 * application already carries OpenPDF and a browser-grade HTML renderer would be
 * a second, much heavier dependency for one document.</p>
 *
 * <p><strong>Typefaces.</strong> Text is set in Manrope and the figures, IDs and
 * PANs in IBM Plex Mono, as in the reference design. The four font files travel
 * inside the artifact (see {@link Typeface}) so the letter renders the same on a
 * developer's machine and on the server; a file that is missing degrades to
 * Helvetica rather than failing the letter.</p>
 *
 * <p><strong>This class computes nothing.</strong> Every figure arrives already
 * calculated and rounded on {@link DealConfirmationSheetValues}; the renderer
 * only decides where each one is printed and how it is formatted. That is what
 * keeps the letter and the deal from disagreeing about what was charged.</p>
 *
 * <p><strong>Values this application does not hold print as an em dash.</strong>
 * The customer's PAN, DP name, DP ID, client ID and bank account have no source
 * anywhere in the platform, so the letter shows {@code —} in their place rather
 * than inventing one. A placeholder reads as "not recorded"; a guess on a
 * contract does not.</p>
 *
 * <p><strong>The letterhead image is optional.</strong> A missing classpath
 * resource degrades to a letter without its masthead and logs the reason; it is
 * not a reason to withhold a customer's confirmation.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DealConfirmationPdfServiceImpl implements DealConfirmationPdfService {

    private static final String LETTERHEAD_RESOURCE = "/logo/alltime_header.png";

    /** The registered entity, as printed in the letter's footer and metadata. */
    private static final String LEGAL_NAME = "All Time Securities Pvt. Ltd.";

    private static final String FOOTER_LINE =
            "Plot No 83-84, Pocket D-14, Shiva Road, Sector-8, Rohini, Delhi-110085"
                    + "   ·   +91-11-47012101"
                    + "   ·   alltime@alltime.co.in"
                    + "   ·   www.alltime.co.in";

    /*
     * The palette is taken from deal-confirmation.html. The two brand colours
     * make up the rule under the masthead and the eyebrow above the reference;
     * the rest are the card, divider and total-bar tints the HTML uses.
     */
    private static final Color BLUE = new Color(0x4F, 0x8F, 0xD0);
    private static final Color PURPLE = new Color(0x4B, 0x2A, 0x8A);
    private static final Color INK = new Color(0x25, 0x26, 0x2D);
    private static final Color MUTED = new Color(0x5A, 0x61, 0x72);
    private static final Color CARD_BORDER = new Color(0xE1, 0xE4, 0xEC);

    /**
     * A whisper of grey so the cards read as panels on the page rather than
     * merging into it. Far lighter than the divider tint, so it never competes
     * with the rules inside a card.
     */
    private static final Color CARD_BG = new Color(0xF7, 0xF8, 0xFA);

    /** How far a card's corners are rounded, and how heavy its outline is. */
    private static final float CARD_RADIUS = 8f;
    private static final float CARD_BORDER_WIDTH = 0.9f;

    private static final Color DIVIDER = new Color(0xED, 0xEF, 0xF4);
    private static final Color TOTAL_BG = new Color(0xEA, 0xF1, 0xFA);
    private static final Color TOTAL_BORDER = new Color(0xC9, 0xDB, 0xF2);
    private static final Color FOOTER_TEXT = new Color(0xD5, 0xD9, 0xE4);

    /*
     * The typefaces. Static files, not the variable font: OpenPDF cannot pick a
     * weight out of a variable font, so each weight is its own file. Google
     * Fonts ships them in the "static" folder of the download.
     *
     *   src/main/resources/fonts/Manrope-Regular.ttf
     *   src/main/resources/fonts/Manrope-Bold.ttf
     *   src/main/resources/fonts/Manrope-ExtraBold.ttf
     *   src/main/resources/fonts/IBMPlexMono-Regular.ttf
     *   src/main/resources/fonts/IBMPlexMono-SemiBold.ttf
     */
    private static final Typeface REGULAR =
            new Typeface("/fonts/Manrope-Regular.ttf", FontFactory.HELVETICA, Font.NORMAL);
    private static final Typeface BOLD =
            new Typeface("/fonts/Manrope-Bold.ttf", FontFactory.HELVETICA_BOLD, Font.BOLD);
    private static final Typeface EXTRA_BOLD =
            new Typeface("/fonts/Manrope-ExtraBold.ttf", FontFactory.HELVETICA_BOLD, Font.BOLD);
    private static final Typeface MONO =
            new Typeface("/fonts/IBMPlexMono-SemiBold.ttf", FontFactory.COURIER_BOLD, Font.BOLD);

    /**
     * The same monospaced face at its book weight. Figures that are computed
     * (amounts, ISINs, codes) are set in this rather than {@link #MONO}: the
     * label beside a value carries the emphasis, the value itself is plain.
     */
    private static final Typeface MONO_PLAIN =
            new Typeface("/fonts/IBMPlexMono-Regular.ttf", FontFactory.COURIER, Font.NORMAL);

    /**
     * The currency printed before the total. "INR" is safe in every font; switch
     * to "₹ " once you have confirmed the bundled IBM Plex Mono file draws the
     * rupee sign.
     */
    private static final String CURRENCY = "INR ";

    /** A missing value, printed where the letter has a place for it. */
    private static final String NONE = "—";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final ThreadLocal<DecimalFormat> MONEY = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    /** A price keeps up to four decimals; the paisa are not the whole figure. */
    private static final ThreadLocal<DecimalFormat> PRICE = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.00##", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    /** Bond quantities are whole units. */
    private static final ThreadLocal<DecimalFormat> QUANTITY = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    private static final ThreadLocal<DecimalFormat> RATE = ThreadLocal.withInitial(
            () -> new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    private final DocumentProperties properties;

    @Override
    public byte[] render(DealConfirmationSheetValues values) {

        if (values == null) {
            throw new DocumentGenerationException(
                    "No deal confirmation values were supplied, so no letter can be drawn");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4, 28, 28, 24, 24);

        document.addTitle("Deal Confirmation - " + values.dealReference());
        document.addAuthor(LEGAL_NAME);
        document.addCreator("CLICK4BOND");

        try {

            PdfWriter.getInstance(document, out);

            document.open();

            addLetterhead(document);
            addCounterparty(document, values);
            addBondAndDealTerms(document, values);
            addOurDetails(document);
            addCustomerDetails(document, values);
            addDisclaimer(document);
            addSignatures(document, values);
            addFooter(document);

            document.close();

        } catch (DocumentException failure) {

            log.error(
                    "Failed to draw the deal confirmation PDF for deal={}",
                    values.dealReference(),
                    failure);

            throw new DocumentGenerationException(
                    "Could not generate the deal confirmation PDF for "
                            + values.dealReference(),
                    failure);

        } finally {

            /*
             * A close() after the explicit one is a no-op, but the guard keeps a
             * failure part way through from leaving the writer's buffers
             * unflushed while a later close() tries to finish the document.
             */
            if (document.isOpen()) {
                document.close();
            }
        }

        return out.toByteArray();
    }

    // =========================================================
    // SECTIONS
    // =========================================================

    private void addLetterhead(Document document) throws DocumentException {

        byte[] logo = letterhead();

        if (logo != null) {

            try {

                Image image = Image.getInstance(logo);
                float width = contentWidth(document);

                image.scaleToFit(width, width * image.getHeight() / image.getWidth());
                image.setAlignment(Element.ALIGN_CENTER);

                document.add(image);

            } catch (DocumentException | IOException failure) {

                /*
                 * A masthead that will not decode is not worth withholding a
                 * confirmation over; the rest of the letter identifies the
                 * issuer.
                 */
                log.warn("Could not place {}, the letter continues without it", LETTERHEAD_RESOURCE, failure);
            }
        }

        PdfPTable band = new PdfPTable(new float[] { 60f, 40f });
        band.setWidthPercentage(100f);
        band.setSpacingBefore(2f);
        band.setSpacingAfter(8f);

        band.addCell(bandCell(BLUE));
        band.addCell(bandCell(PURPLE));

        document.add(band);
    }

    private void addCounterparty(Document document, DealConfirmationSheetValues values)
            throws DocumentException {

        PdfPTable layout = new PdfPTable(new float[] { 62f, 38f });
        layout.setWidthPercentage(100f);

        PdfPCell left = borderless();
        left.addElement(label("Counterparty"));
        left.addElement(name(values.counterpartyName()));
        left.addElement(muted("Address: " + NONE, 10f));
        layout.addCell(left);

        PdfPCell right = borderless();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph eyebrow = new Paragraph("DEAL CONFIRMATION", BOLD.at(8.5f, PURPLE));
        eyebrow.setAlignment(Element.ALIGN_RIGHT);
        eyebrow.setSpacingBefore(1f);
        right.addElement(eyebrow);

        Paragraph reference = new Paragraph(text(values.dealReference()), MONO.at(11f, INK));
        reference.setAlignment(Element.ALIGN_RIGHT);
        reference.setSpacingBefore(3f);
        right.addElement(reference);

        Paragraph date = new Paragraph(date(values.dealDate()), REGULAR.at(10f, MUTED));
        date.setAlignment(Element.ALIGN_RIGHT);
        date.setSpacingBefore(1f);
        right.addElement(date);

        layout.addCell(right);

        document.add(fullWidthCard(layout));
        document.add(intro());
    }

    private void addBondAndDealTerms(Document document, DealConfirmationSheetValues values)
            throws DocumentException {

        PdfPTable cards = new PdfPTable(new float[] { 49f, 2f, 49f });
        cards.setWidthPercentage(100f);
        cards.setSpacingBefore(6f);

        cards.addCell(cardCell(bondDetails(values)));
        cards.addCell(empty());

        cards.addCell(cardCell(dealTerms(values)));

        document.add(cards);
    }

    private PdfPTable bondDetails(DealConfirmationSheetValues values) {

        PdfPTable block = column();

        addBlock(block, label("Bond details"));
        addBlock(block, rule());

        PdfPTable rows = keyValues();

        addKeyValue(rows, "Security name", text(values.securityName()), false);
        addKeyValue(rows, "ISIN no.", text(values.isin()), true);
        addKeyValue(rows, "Coupon rate", percent(values.couponRate()), true);
        addKeyValue(rows, "Maturity date", date(values.maturityDate()), false);
        addKeyValue(rows, "Interest payment date", text(values.ipDateDescription()), false);
        addKeyValue(rows, "Last interest payment", date(values.lastInterestPaymentDate()), false);
        addKeyValue(rows, "Principal amount", money(values.principalAmount()), true);
        addKeyValue(rows, "Accrued interest", money(values.accruedInterest()), true);
        addKeyValue(rows, "Stamp duty", money(values.stampDuty()), true);

        addBlock(block, rows);

        return block;
    }

    private PdfPTable dealTerms(DealConfirmationSheetValues values) {

        PdfPTable block = column();

        addBlock(block, label("Deal terms"));
        addBlock(block, rule());

        PdfPTable rows = keyValues();

        addKeyValue(rows, "Type of transaction", text(values.transactionType()), false);
        addKeyValue(rows, "Mode of delivery", text(values.modeOfDelivery()), false);
        addKeyValue(rows, "Deal date", date(values.dealDate()), false);
        addKeyValue(rows, "Value date", date(values.valueDate()), false);
        addKeyValue(rows, "Price", price(values.price()), true);
        addKeyValue(rows, "No. of bonds", quantity(values.numberOfBonds()), true);
        addKeyValue(rows, "Quantum", money(values.quantum()), true);
        addKeyValue(rows, "No. of accrued days", count(values.accruedDays()), true);

        addBlock(block, rows);
        addBlock(block, gap(6f));
        addBlock(block, totalConsideration(values.totalConsideration()));

        return block;
    }

    private void addOurDetails(Document document) throws DocumentException {

        DocumentProperties.Organisation organisation = properties.getOrganisation();

        PdfPTable block = column();

        PdfPTable heading = new PdfPTable(new float[] { 70f, 30f });
        heading.setWidthPercentage(100f);

        PdfPCell beneficiary = borderless();
        beneficiary.addElement(label("Our details · Beneficiary name"));
        beneficiary.addElement(new Paragraph(
                text(organisation.getBeneficiaryName()),
                REGULAR.at(14f, INK)));
        heading.addCell(beneficiary);

        PdfPCell pan = borderless();
        pan.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph panLabel = label("Our PAN");
        panLabel.setAlignment(Element.ALIGN_RIGHT);
        pan.addElement(panLabel);
        Paragraph panValue = new Paragraph(text(organisation.getPan()), MONO_PLAIN.at(12f, INK));
        panValue.setAlignment(Element.ALIGN_RIGHT);
        pan.addElement(panValue);
        heading.addCell(pan);

        addBlock(block, heading);
        addBlock(block, gap(4f));
        addBlock(block, divider());

        PdfPTable stats = stats(4);

        addStatLabels(stats, "CM-BP-Id", "CM name", "Market type", "Settlement no.");
        addStatValues(
                stats,
                Stat.mono(text(organisation.getCmBpId())),
                Stat.plain(text(organisation.getCmName())),
                Stat.plain(text(organisation.getMarketType())),
                Stat.plain(text(organisation.getSettlementNumber())));

        addStatLabels(stats, "IFSC code", "Banker", "", "");
        addStatValues(
                stats,
                Stat.mono(text(organisation.getIfscCode())),
                Stat.plain(text(organisation.getBanker())),
                Stat.plain(""),
                Stat.plain(""));

        addBlock(block, stats);

        document.add(fullWidthCard(block));
    }

    private void addCustomerDetails(Document document, DealConfirmationSheetValues values)
            throws DocumentException {

        PdfPTable block = column();

        PdfPTable heading = new PdfPTable(new float[] { 70f, 30f });
        heading.setWidthPercentage(100f);

        PdfPCell name = borderless();
        name.addElement(label("Customer details · Name"));
        name.addElement(new Paragraph(
                text(values.counterpartyName()),
                REGULAR.at(14f, INK)));
        heading.addCell(name);

        PdfPCell pan = borderless();
        pan.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph panLabel = label("Your PAN");
        panLabel.setAlignment(Element.ALIGN_RIGHT);
        pan.addElement(panLabel);
        Paragraph panValue = new Paragraph(NONE, MONO_PLAIN.at(12f, INK));
        panValue.setAlignment(Element.ALIGN_RIGHT);
        pan.addElement(panValue);
        heading.addCell(pan);

        addBlock(block, heading);
        addBlock(block, gap(4f));
        addBlock(block, divider());

        PdfPTable stats = stats(4);

        addStatLabels(stats, "Your DP name", "DP ID", "Client ID", "HDFC Bank Limited");
        addStatValues(
                stats,
                Stat.plain(NONE),
                Stat.mono(NONE),
                Stat.mono(NONE),
                Stat.mono(NONE));

        addBlock(block, stats);

        document.add(fullWidthCard(block));
    }

    private void addDisclaimer(Document document) throws DocumentException {

        document.add(gap(8f));
        document.add(divider());

        Paragraph disclaimer = new Paragraph(DISCLAIMER, REGULAR.at(7.5f, MUTED));

        disclaimer.setLeading(10.5f);
        disclaimer.setSpacingBefore(6f);

        document.add(disclaimer);
    }

    private void addSignatures(Document document, DealConfirmationSheetValues values)
            throws DocumentException {

        PdfPTable signatures = new PdfPTable(new float[] { 48f, 4f, 48f });
        signatures.setWidthPercentage(100f);
        signatures.setSpacingBefore(10f);

        signatures.addCell(signatureText("for " + LEGAL_NAME));
        signatures.addCell(empty());
        signatures.addCell(signatureText("We Confirm the transaction as specified above"));

        signatures.addCell(signatureLine());
        signatures.addCell(empty());
        signatures.addCell(signatureLine());

        signatures.addCell(empty());
        signatures.addCell(empty());
        signatures.addCell(signatureText(
                "Counterparty Name- " + text(values.counterpartyName())));

        signatures.addCell(signatureText("Authorised Signatory"));
        signatures.addCell(empty());
        signatures.addCell(signatureText("Authorised Signatory"));

        document.add(signatures);
    }

    private void addFooter(Document document) throws DocumentException {

        PdfPTable footer = new PdfPTable(1);
        footer.setWidthPercentage(100f);
        footer.setSpacingBefore(10f);

        PdfPCell cell = new PdfPCell(new Phrase(FOOTER_LINE, REGULAR.at(8f, FOOTER_TEXT)));

        cell.setBackgroundColor(INK);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(6f);
        cell.setPaddingBottom(6f);
        cell.setPaddingLeft(10f);
        cell.setPaddingRight(10f);

        footer.addCell(cell);

        document.add(footer);
    }

    // =========================================================
    // BUILDING BLOCKS
    // =========================================================

    /**
     * The disclaimer, carried over from {@code deal-confirmation.html}.
     *
     * <p>It is the customer's acknowledgement that the transaction was entered
     * into freely and that this application's letter does not replace the offer
     * documents. It is reproduced unchanged rather than paraphrased.</p>
     */
    private static final String DISCLAIMER =
            "The above deal/transaction has been done by us at your own free will and consent and "
                    + LEGAL_NAME + " and/or its employees have not induced me/us to deal or transact. "
                    + "Before making investment/subscribing to said securities, you have read all the "
                    + "related transaction documents including offer document, instrument description, "
                    + "term sheet, security features, statutory filings, issuer information, rating "
                    + "letter(s), rating rationale(s) etc. You also fully understand the various risks "
                    + "associated with investing/subscribing to said securities including but not limited "
                    + "to credit risk, market risk, default risk, liquidity risk, interest rate risk, "
                    + "reinvestment risk etc. associated with trading in corporate bonds, preference "
                    + "shares and other fixed income securities. " + LEGAL_NAME + " will not be "
                    + "responsible for any default of payment of interest & principal amount by the "
                    + "issuer. Please confirm the same by return mail on alltimesec@gmail.com. Please "
                    + "note that in case we do not receive the copy of the deal confirmation signed by "
                    + "me/us in acceptance thereof within 3 days from the date of deal settled by me/us, "
                    + "nor do we receive any written communication raising any objection or query, it "
                    + "will be deemed that you have accepted the confirmation in all respects.";

    private Paragraph intro() {

        Paragraph paragraph = new Paragraph(
                "Dear Sir, we hereby confirm the following transaction:",
                REGULAR.at(10.5f, MUTED));

        paragraph.setSpacingBefore(8f);
        paragraph.setSpacingAfter(0f);

        return paragraph;
    }

    /** A card's eyebrow: the small upper-case heading above its content. */
    private Paragraph label(String text) {

        Paragraph paragraph = new Paragraph(
                text.toUpperCase(Locale.ENGLISH),
                BOLD.at(7.5f, MUTED));

        paragraph.setSpacingBefore(1f);
        paragraph.setSpacingAfter(1f);

        return paragraph;
    }

    private Paragraph name(String text) {

        Paragraph paragraph = new Paragraph(text(text), EXTRA_BOLD.at(19f, INK));

        paragraph.setSpacingBefore(2f);
        paragraph.setSpacingAfter(2f);

        return paragraph;
    }

    private Paragraph muted(String text, float size) {

        return new Paragraph(text, REGULAR.at(size, MUTED));
    }

    /** The short blue rule under a card's heading. */
    private PdfPTable rule() {

        PdfPTable rule = new PdfPTable(1);
        rule.setTotalWidth(24f);
        rule.setLockedWidth(true);
        rule.setSpacingBefore(1f);
        rule.setSpacingAfter(3f);

        PdfPCell cell = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        cell.setBackgroundColor(BLUE);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(2f);

        rule.addCell(cell);

        return rule;
    }

    private PdfPTable divider() {

        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100f);
        divider.setSpacingAfter(4f);

        PdfPCell cell = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        cell.setBackgroundColor(CARD_BORDER);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(0.75f);

        divider.addCell(cell);

        return divider;
    }

    private PdfPTable gap(float height) {

        PdfPTable gap = new PdfPTable(1);
        gap.setWidthPercentage(100f);

        PdfPCell cell = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(height);

        gap.addCell(cell);

        return gap;
    }

    /** A one-column table, so blocks stack without leaving inter-cell gaps. */
    private PdfPTable column() {

        PdfPTable column = new PdfPTable(1);
        column.setWidthPercentage(100f);

        return column;
    }

    private void addBlock(PdfPTable column, Element element) {

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        cell.addElement(element);

        column.addCell(cell);
    }

    /** Wraps content in the rounded, faintly grey card the letter uses. */
    private PdfPTable fullWidthCard(PdfPTable content) {

        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100f);
        card.setSpacingBefore(6f);

        card.addCell(cardCell(content));

        return card;
    }

    private PdfPCell cardCell(PdfPTable content) {

        /*
         * OpenPDF's cells draw square borders only, so the card's fill and
         * outline are painted by a cell event instead: a rounded rectangle laid
         * down on the background canvas leaves the nested table's own text and
         * rules untouched.
         */
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(9f);
        cell.setCellEvent(new RoundedCard());
        cell.addElement(content);

        return cell;
    }

    private PdfPTable keyValues() {

        PdfPTable table = new PdfPTable(new float[] { 46f, 54f });
        table.setWidthPercentage(100f);

        return table;
    }

    /**
     * One label/value row. The column name carries the emphasis and the value is
     * set plain, so the eye lands on what each figure means before the figure
     * itself. Figures, IDs and codes are set in the monospaced face
     * ({@code mono}), words and dates in Manrope, as in the reference design.
     */
    private void addKeyValue(PdfPTable table, String key, String value, boolean mono) {

        PdfPCell keyCell = new PdfPCell(new Phrase(key, BOLD.at(10f, MUTED)));
        keyCell.setBorder(Rectangle.BOTTOM);
        keyCell.setBorderColor(DIVIDER);
        keyCell.setBorderWidth(0.75f);
        keyCell.setPaddingTop(3.5f);
        keyCell.setPaddingBottom(3.5f);
        keyCell.setPaddingLeft(0f);
        keyCell.setPaddingRight(3f);

        Font valueFont = mono ? MONO_PLAIN.at(9.5f, INK) : REGULAR.at(10f, INK);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(DIVIDER);
        valueCell.setBorderWidth(0.75f);
        valueCell.setPaddingTop(3.5f);
        valueCell.setPaddingBottom(3.5f);
        valueCell.setPaddingLeft(3f);
        valueCell.setPaddingRight(0f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(keyCell);
        table.addCell(valueCell);
    }

    private PdfPTable stats(int columns) {

        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100f);

        return table;
    }

    private void addStatLabels(PdfPTable table, String... headings) {

        for (String heading : headings) {

            PdfPCell cell = heading.isEmpty() ? empty() : borderless();
            cell.setPaddingTop(6f);
            cell.setPaddingBottom(0f);

            if (!heading.isEmpty()) {
                cell.addElement(label(heading));
            }

            table.addCell(cell);
        }
    }

    private void addStatValues(PdfPTable table, Stat... stats) {

        for (Stat stat : stats) {

            PdfPCell cell = stat.text().isEmpty() ? empty() : borderless();
            cell.setPaddingTop(1f);
            cell.setPaddingBottom(3f);

            if (!stat.text().isEmpty()) {

                Font font = stat.mono() ? MONO_PLAIN.at(11f, INK) : REGULAR.at(11.5f, INK);

                cell.addElement(new Paragraph(stat.text(), font));
            }

            table.addCell(cell);
        }
    }

    private PdfPTable totalConsideration(BigDecimal total) {

        PdfPTable table = new PdfPTable(new float[] { 50f, 50f });
        table.setWidthPercentage(100f);

        PdfPCell label = new PdfPCell(new Phrase("Total consideration", BOLD.at(10.5f, INK)));
        label.setBackgroundColor(TOTAL_BG);
        label.setBorderColor(TOTAL_BORDER);
        label.setBorderWidth(0.9f);
        label.setPadding(5f);

        PdfPCell value = new PdfPCell(new Phrase(CURRENCY + money(total), MONO.at(11f, INK)));
        value.setBackgroundColor(TOTAL_BG);
        value.setBorderColor(TOTAL_BORDER);
        value.setBorderWidth(0.9f);
        value.setPadding(5f);
        value.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(label);
        table.addCell(value);

        return table;
    }

    private PdfPCell bandCell(Color color) {

        PdfPCell cell = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        cell.setBackgroundColor(color);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(4f);

        return cell;
    }

    private PdfPCell signatureText(String text) {

        PdfPCell cell = borderless();
        cell.setPaddingTop(4f);

        Paragraph paragraph = new Paragraph(text, BOLD.at(10f, INK));
        paragraph.setSpacingBefore(0f);

        cell.addElement(paragraph);

        return cell;
    }

    private PdfPCell signatureLine() {

        PdfPCell cell = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(INK);
        cell.setBorderWidth(0.9f);
        cell.setFixedHeight(32f);

        return cell;
    }

    private PdfPCell borderless() {

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        cell.setVerticalAlignment(Element.ALIGN_TOP);

        return cell;
    }

    private PdfPCell empty() {

        /*
         * A space rather than a truly empty cell: OpenPDF refuses a cell with no
         * content at layout time, and these cells exist only to hold a column
         * open or to leave a signature row blank.
         */
        PdfPCell cell = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);

        return cell;
    }

    /**
     * Paints a card: the faint grey fill and hairline outline, both with rounded
     * corners.
     *
     * <p>Drawn on the cell's background canvas so it sits behind the card's
     * contents. The rectangle is inset by half the stroke width, otherwise the
     * outer half of the outline would be clipped at the cell's edge.</p>
     */
    private static final class RoundedCard implements PdfPCellEvent {

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {

            float inset = CARD_BORDER_WIDTH / 2f;

            float x = position.getLeft() + inset;
            float y = position.getBottom() + inset;
            float width = position.getWidth() - 2 * inset;
            float height = position.getHeight() - 2 * inset;

            PdfContentByte canvas = canvases[PdfPTable.BACKGROUNDCANVAS];

            canvas.saveState();

            canvas.setColorFill(CARD_BG);
            canvas.roundRectangle(x, y, width, height, CARD_RADIUS);
            canvas.fill();

            canvas.setColorStroke(CARD_BORDER);
            canvas.setLineWidth(CARD_BORDER_WIDTH);
            canvas.roundRectangle(x, y, width, height, CARD_RADIUS);
            canvas.stroke();

            canvas.restoreState();
        }
    }

    // =========================================================
    // FORMATTING
    // =========================================================

    private static String text(String value) {

        return value == null || value.isBlank() ? NONE : value;
    }

    private static String date(LocalDate value) {

        return value == null ? NONE : value.format(DATE);
    }

    private static String money(BigDecimal value) {

        return value == null ? NONE : MONEY.get().format(value);
    }

    private static String price(BigDecimal value) {

        return value == null ? NONE : PRICE.get().format(value);
    }

    private static String quantity(Long value) {

        return value == null ? NONE : QUANTITY.get().format(value);
    }

    private static String count(Long value) {

        return value == null ? NONE : String.valueOf(value);
    }

    /** The rate is stored as a percentage, so it prints with the sign appended. */
    private static String percent(BigDecimal value) {

        return value == null ? NONE : RATE.get().format(value) + "%";
    }

    private static float contentWidth(Document document) {

        return document.getPageSize().getWidth()
                - document.leftMargin()
                - document.rightMargin();
    }

    /**
     * Reads the masthead from the classpath.
     *
     * <p>Read as bytes rather than a path: the image travels inside the jar on
     * the server and sits in a directory during development, and the byte-array
     * form behaves the same in both.</p>
     *
     * @return the image bytes, or null when the resource is not on the classpath
     */
    private static byte[] letterhead() {

        try (InputStream stream =
                     DealConfirmationPdfServiceImpl.class.getResourceAsStream(LETTERHEAD_RESOURCE)) {

            if (stream == null) {

                log.warn("{} is not on the classpath, the letter has no masthead", LETTERHEAD_RESOURCE);
                return null;
            }

            return stream.readAllBytes();

        } catch (IOException failure) {

            log.warn("Could not read {}, the letter has no masthead", LETTERHEAD_RESOURCE, failure);
            return null;
        }
    }

    // =========================================================
    // TYPEFACES
    // =========================================================

    /** A value in a stat grid, and whether it is set in the monospaced face. */
    private record Stat(String text, boolean mono) {

        static Stat plain(String text) {
            return new Stat(text, false);
        }

        static Stat mono(String text) {
            return new Stat(text, true);
        }
    }

    /**
     * One weight of a bundled typeface.
     *
     * <p>The font is read from the classpath as bytes and embedded, so the weight
     * survives on a machine that has neither Manrope nor IBM Plex Mono
     * installed. Loading is deliberately forgiving: a file that is missing or
     * unreadable degrades to the fallback base-14 font and logs the reason,
     * because a letter set in Helvetica still reads correctly whereas one that
     * fails to render is a confirmation the customer cannot get.</p>
     */
    private static final class Typeface {

        private final BaseFont base;
        private final String fallbackFamily;
        private final int fallbackStyle;

        private Typeface(String resource, String fallbackFamily, int fallbackStyle) {

            this.fallbackFamily = fallbackFamily;
            this.fallbackStyle = fallbackStyle;
            this.base = load(resource);
        }

        Font at(float size, Color color) {

            return base == null
                    ? FontFactory.getFont(fallbackFamily, size, fallbackStyle, color)
                    : new Font(base, size, Font.NORMAL, color);
        }

        private static BaseFont load(String resource) {

            try (InputStream stream = DealConfirmationPdfServiceImpl.class.getResourceAsStream(resource)) {

                if (stream == null) {

                    log.warn("{} is not on the classpath, falling back to a base-14 font", resource);
                    return null;
                }

                String fileName = resource.substring(resource.lastIndexOf('/') + 1);

                // IDENTITY_H is required for a Unicode face; EMBEDDED keeps it in the PDF.
                return BaseFont.createFont(
                        fileName,
                        BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED,
                        true,
                        stream.readAllBytes(),
                        null);

            } catch (DocumentException | IOException failure) {

                log.warn("Could not load {}, falling back to a base-14 font", resource, failure);
                return null;
            }
        }
    }
}