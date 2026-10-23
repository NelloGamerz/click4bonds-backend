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
import com.lowagie.text.Chunk;
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
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Draws the corporate deal confirmation letter as a PDF.
 *
 * <p>The letter is set on a clean white page with navy and cyan accents, soft
 * light-blue section panels, hairline borders and a faint Click4Bonds watermark.
 * The masthead and the address bar belong to the page and are drawn on every
 * page; the rest is laid out row by row with OpenPDF. The letterhead image is
 * reused exactly as supplied.</p>
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

    /*
     * Page geometry. The masthead and the address bar belong to the page rather
     * than to the content, so their heights are reserved out of the margins and
     * a second page repeats them automatically.
     */
    private static final float LEFT_MARGIN = 30f;
    private static final float RIGHT_MARGIN = 30f;

    /** Clearance above the masthead at the top of each page. */
    private static final float HEADER_TOP_GAP = 17f;

    /** The gap between the masthead image and the navy/cyan rule. */
    private static final float MASTHEAD_BAND_GAP = 5f;

    private static final float BAND_HEIGHT = 2.5f;

    /** The gap between the header rule and the first line of body text. */
    private static final float HEADER_CONTENT_GAP = 9f;

    /** Zero: the address bar is anchored flush to the bottom edge of the page. */
    private static final float FOOTER_BOTTOM_GAP = 0f;
    private static final float FOOTER_HEIGHT = 26f;

    /** The address bar plus its clearance from the body text above it. */
    private static final float BOTTOM_MARGIN = FOOTER_BOTTOM_GAP + FOOTER_HEIGHT + 10f;

    /** The registered entity, as printed in the letter and its metadata. */
    private static final String LEGAL_NAME = "All Time Securities Pvt. Ltd.";

    private static final String FOOTER_LINE =
            "Plot No 83-84, Pocket D-14, Shiva Road, Sector-8, Rohini, Delhi-110085"
                    + "   ·   +91-11-47012101"
                    + "   ·   alltime@alltime.co.in"
                    + "   ·   www.alltime.co.in";

    /*
     * The palette: deep navy for emphasis, cyan for accents, very light blue for
     * the section panels, and a cool grey for secondary text. Every tint is pale
     * enough to print legibly on monochrome office printers.
     */
    private static final Color NAVY = new Color(0x0B, 0x24, 0x47);
    private static final Color NAVY_SOFT = new Color(0x1B, 0x3E, 0x6B);
    private static final Color CYAN = new Color(0x0E, 0x9F, 0xC6);
    private static final Color INK = new Color(0x25, 0x26, 0x2D);
    private static final Color MUTED = new Color(0x5A, 0x68, 0x7A);
    private static final Color WHITE = Color.WHITE;

    private static final Color CARD_BG = new Color(0xE8, 0xF2, 0xFC);
    private static final Color CARD_BORDER = new Color(0xC7, 0xDB, 0xEF);
    private static final Color DIVIDER = new Color(0xD6, 0xE4, 0xF3);

    /*
     * The page washes: a pale blue at the head and foot of the page, each
     * fading to white where the body text sits. The corner rings and watermark
     * are set a shade deeper than the washes so the page has depth without
     * competing with the type.
     */
    private static final Color PAGE_TOP_TINT = new Color(0xE5, 0xF0, 0xFB);
    private static final Color PAGE_BOTTOM_TINT = new Color(0xEB, 0xF4, 0xFC);
    private static final Color CORNER_PATTERN = new Color(0xD4, 0xE5, 0xF5);
    private static final Color WATERMARK = new Color(0xDD, 0xEA, 0xF7);

    /** The highlighted total row: pale cyan behind navy type. */
    private static final Color TOTAL_BG = new Color(0xD9, 0xEF, 0xF9);

    private static final Color FOOTER_BG = new Color(0xF3, 0xF8, 0xFD);
    private static final Color FOOTER_TEXT = new Color(0x3B, 0x55, 0x73);

    /** How far a card's corners are rounded, and how heavy its outline is. */
    private static final float CARD_RADIUS = 8f;
    private static final float CARD_BORDER_WIDTH = 0.8f;

    /**
     * The typefaces. Helvetica is one of the fourteen fonts every PDF reader
     * carries, so the letter renders identically without embedding a font file
     * or depending on a font being installed on the server.
     */
    private static final Typeface REGULAR = new Typeface(FontFactory.HELVETICA, Font.NORMAL);
    private static final Typeface BOLD = new Typeface(FontFactory.HELVETICA_BOLD, Font.BOLD);
    private static final Typeface EXTRA_BOLD = new Typeface(FontFactory.HELVETICA_BOLD, Font.BOLD);
    private static final Typeface MONO = new Typeface(FontFactory.HELVETICA_BOLD, Font.BOLD);

    /**
     * The currency printed before the total. "INR" is safe in every font; switch
     * to "₹ " once the rupee sign has been verified in the generated PDF.
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

        /*
         * The masthead and the address bar are drawn on every page by a page
         * event rather than added once to the content, so a letter that runs to
         * a second page still carries both. Their height is reserved out of the
         * page margins, which is what keeps the body from printing over them.
         */
        float availableWidth = PageSize.A4.getWidth() - LEFT_MARGIN - RIGHT_MARGIN;

        Image logo = letterheadImage(availableWidth);

        float mastheadHeight = logo == null ? 0f : logo.getScaledHeight();
        float topMargin = HEADER_TOP_GAP + mastheadHeight
                + (logo == null ? 0f : MASTHEAD_BAND_GAP) + BAND_HEIGHT + HEADER_CONTENT_GAP;

        Document document = new Document(
                PageSize.A4, LEFT_MARGIN, RIGHT_MARGIN, topMargin, BOTTOM_MARGIN);

        document.addTitle("Deal Confirmation - " + values.dealReference());
        document.addAuthor(LEGAL_NAME);
        document.addCreator("CLICK4BOND");

        try {

            PdfWriter writer = PdfWriter.getInstance(document, out);

            writer.setPageEvent(new LetterheadAndFooter(logo, availableWidth));

            document.open();

            addCounterparty(document, values);
            addBondAndDealTerms(document, values);
            addOurDetails(document);
            addCustomerDetails(document, values);
            addDisclaimer(document);
            addSignatures(document, values);

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

    private void addCounterparty(Document document, DealConfirmationSheetValues values)
            throws DocumentException {

        PdfPTable layout = new PdfPTable(new float[] { 62f, 38f });
        layout.setWidthPercentage(100f);

        PdfPCell left = borderless();
        left.addElement(label("Counterparty"));
        left.addElement(name(values.counterpartyName()));
        left.addElement(muted("Address: " + NONE, 9.2f));
        layout.addCell(left);

        PdfPCell right = borderless();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph eyebrow = new Paragraph("DEAL CONFIRMATION", BOLD.at(8f, CYAN));
        eyebrow.setAlignment(Element.ALIGN_RIGHT);
        eyebrow.setSpacingBefore(0f);
        right.addElement(eyebrow);

        Paragraph reference = new Paragraph(text(values.dealReference()), BOLD.at(10.5f, NAVY));
        reference.setAlignment(Element.ALIGN_RIGHT);
        reference.setSpacingBefore(2f);
        right.addElement(reference);

        Paragraph date = new Paragraph(date(values.dealDate()), REGULAR.at(8.5f, MUTED));
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
        cards.setSpacingBefore(4f);

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

        addKeyValue(rows, "Type of transaction", text(values.transactionType()));
        addKeyValue(rows, "Mode of delivery", text(values.modeOfDelivery()));
        addKeyValue(rows, "Deal date", date(values.dealDate()));
        addKeyValue(rows, "Value date", date(values.valueDate()));
        addKeyValue(rows, "Security name", text(values.securityName()));
        addKeyValue(rows, "ISIN no.", text(values.isin()));
        addKeyValue(rows, "Coupon rate", percent(values.couponRate()));
        addKeyValue(rows, "Maturity date", date(values.maturityDate()));
        addKeyValue(rows, "Last interest payment", date(values.lastInterestPaymentDate()));

        addBlock(block, rows);

        return block;
    }

    private PdfPTable dealTerms(DealConfirmationSheetValues values) {

        PdfPTable block = column();

        addBlock(block, label("Deal terms"));
        addBlock(block, rule());

        PdfPTable rows = keyValues();

        addKeyValue(rows, "Interest payment date", text(values.ipDateDescription()));
        addKeyValue(rows, "Price", price(values.price()));
        addKeyValue(rows, "No. of bonds", quantity(values.numberOfBonds()));
        addKeyValue(rows, "Quantum", money(values.quantum()));
        addKeyValue(rows, "Principal amount", money(values.principalAmount()));
        addKeyValue(rows, "Put / Call", putCall(values.putCallDescription()));
        addKeyValue(rows, "No. of accrued days", count(values.accruedDays()));
        addKeyValue(rows, "Accrued interest", money(values.accruedInterest()));
        addKeyValue(rows, "Stamp duty", money(values.stampDuty()));

        addBlock(block, rows);
        addBlock(block, gap(5f));
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

        Paragraph beneficiaryName = new Paragraph(
                text(organisation.getBeneficiaryName()),
                BOLD.at(11.5f, NAVY));
        beneficiaryName.setSpacingBefore(1f);
        beneficiaryName.setSpacingAfter(0f);
        beneficiary.addElement(beneficiaryName);
        heading.addCell(beneficiary);

        PdfPCell pan = borderless();
        pan.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph panLabel = label("Our PAN");
        panLabel.setAlignment(Element.ALIGN_RIGHT);
        pan.addElement(panLabel);
        Paragraph panValue = new Paragraph(text(organisation.getPan()), MONO.at(10.5f, NAVY));
        panValue.setAlignment(Element.ALIGN_RIGHT);
        panValue.setSpacingBefore(1f);
        pan.addElement(panValue);
        heading.addCell(pan);

        addBlock(block, heading);
        addBlock(block, gap(3f));
        addBlock(block, divider());

        PdfPTable stats = stats(4);

        addStatLabels(stats, "CM-BP-Id", "CM name", "Market type", "Settlement no.");
        addStatValues(
                stats,
                text(organisation.getCmBpId()),
                text(organisation.getCmName()),
                text(organisation.getMarketType()),
                text(organisation.getSettlementNumber()));

        addStatLabels(stats, "IFSC code", "Banker", "", "");
        addStatValues(
                stats,
                text(organisation.getIfscCode()),
                text(organisation.getBanker()),
                "",
                "");

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

        Paragraph customerName = new Paragraph(
                text(values.counterpartyName()),
                BOLD.at(12f, NAVY));
        customerName.setSpacingBefore(1f);
        name.addElement(customerName);
        heading.addCell(name);

        PdfPCell pan = borderless();
        pan.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph panLabel = label("Your PAN");
        panLabel.setAlignment(Element.ALIGN_RIGHT);
        pan.addElement(panLabel);
        Paragraph panValue = new Paragraph(NONE, MONO.at(10.5f, NAVY));
        panValue.setAlignment(Element.ALIGN_RIGHT);
        panValue.setSpacingBefore(1f);
        pan.addElement(panValue);
        heading.addCell(pan);

        addBlock(block, heading);
        addBlock(block, gap(3f));
        addBlock(block, divider());

        PdfPTable stats = stats(4);

        addStatLabels(stats, "Your DP name", "DP ID", "Client ID", "HDFC Bank Limited");
        addStatValues(stats, NONE, NONE, NONE, NONE);

        addBlock(block, stats);

        document.add(fullWidthCard(block));
    }

    private void addDisclaimer(Document document) throws DocumentException {

        document.add(gap(6f));
        document.add(divider());

        Paragraph disclaimer = new Paragraph(DISCLAIMER, REGULAR.at(7.2f, MUTED));

        disclaimer.setLeading(9.4f);
        disclaimer.setAlignment(Element.ALIGN_JUSTIFIED);
        disclaimer.setSpacingBefore(5f);

        document.add(disclaimer);
    }

    /**
     * The signature block is deliberately only captions and clear space.
     *
     * <p>The letter is signed by hand, so there are no signature graphics,
     * rules or "Authorised Signatory" labels: the customer and the company each
     * get an unbroken area in which to sign. The counterparty's name remains
     * beneath their space so the signed letter still identifies them.</p>
     */
    private void addSignatures(Document document, DealConfirmationSheetValues values)
            throws DocumentException {

        PdfPTable signatures = new PdfPTable(new float[] { 48f, 4f, 48f });
        signatures.setWidthPercentage(100f);
        signatures.setSpacingBefore(9f);

        signatures.addCell(signatureText("for " + LEGAL_NAME));
        signatures.addCell(empty());
        signatures.addCell(signatureText("We Confirm the transaction as specified above"));

        signatures.addCell(signatureSpace());
        signatures.addCell(empty());
        signatures.addCell(signatureSpace());

        signatures.addCell(empty());
        signatures.addCell(empty());
        signatures.addCell(signatureText(
                "Counterparty Name- " + text(values.counterpartyName())));

        document.add(signatures);
    }

    // =========================================================
    // BUILDING BLOCKS
    // =========================================================

    /**
     * The disclaimer, carried over unchanged from the existing letter.
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
                "Respected Sir/Madam, we hereby confirm the following transaction:",
                REGULAR.at(10f, MUTED));

        paragraph.setSpacingBefore(7f);
        paragraph.setSpacingAfter(0f);

        return paragraph;
    }

    /** A section's small upper-case heading. */
    private Paragraph label(String text) {

        Paragraph paragraph = new Paragraph(
                text.toUpperCase(Locale.ENGLISH),
                BOLD.at(7f, NAVY_SOFT));

        paragraph.setSpacingBefore(0f);
        paragraph.setSpacingAfter(0f);

        return paragraph;
    }

    private Paragraph name(String text) {

        Paragraph paragraph = new Paragraph(text(text), EXTRA_BOLD.at(17f, NAVY));

        paragraph.setSpacingBefore(1f);
        paragraph.setSpacingAfter(1f);

        return paragraph;
    }

    private Paragraph muted(String text, float size) {

        return new Paragraph(text, REGULAR.at(size, MUTED));
    }

    /** The short cyan rule under a section heading. */
    private PdfPTable rule() {

        PdfPTable rule = new PdfPTable(1);
        rule.setTotalWidth(26f);
        rule.setLockedWidth(true);
        rule.setHorizontalAlignment(Element.ALIGN_LEFT);
        rule.setSpacingAfter(2f);

        /*
         * An explicit spacer row rather than spacingBefore: spacing set on a
         * table nested in a cell is not applied, and without the gap the rule
         * lands on the heading's baseline.
         */
        PdfPCell spacer = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        spacer.setBorder(Rectangle.NO_BORDER);
        spacer.setFixedHeight(3f);
        rule.addCell(spacer);

        PdfPCell cell = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        cell.setBackgroundColor(CYAN);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(2f);

        rule.addCell(cell);

        return rule;
    }

    private PdfPTable divider() {

        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100f);
        divider.setSpacingAfter(2f);

        PdfPCell cell = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        cell.setBackgroundColor(DIVIDER);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(0.6f);

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

    /** Wraps content in the rounded, faintly blue card the letter uses. */
    private PdfPTable fullWidthCard(PdfPTable content) {

        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100f);
        card.setSpacingBefore(4f);

        card.addCell(cardCell(content));

        return card;
    }

    private PdfPCell cardCell(PdfPTable content) {

        /*
         * OpenPDF's cells fill square only, so the card's fill and outline are
         * painted by a cell event instead. The event draws on the table's
         * BACKGROUNDCANVAS, which {@code PdfPTable.endWritingRows} composites
         * beneath the cell's text and nested tables, so the rounded fill cannot
         * cover them. Filling here rather than through setBackgroundColor is
         * what leaves the corners genuinely rounded on top of the page instead
         * of being squared off and painted over.
         */
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(8f);
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
     * One label/value row. The term is set in navy and the figure in plain ink,
     * so the emphasis sits on what is being described rather than on the figure
     * beside it. Every row is closed by a hairline divider rather than a full
     * grid.
     */
    private void addKeyValue(PdfPTable table, String key, String value) {

        PdfPCell keyCell = new PdfPCell(new Phrase(key, REGULAR.at(9.2f, NAVY)));
        keyCell.setBorder(Rectangle.BOTTOM);
        keyCell.setBorderColor(DIVIDER);
        keyCell.setBorderWidth(0.6f);
        keyCell.setPaddingTop(2.7f);
        keyCell.setPaddingBottom(2.7f);
        keyCell.setPaddingLeft(0f);
        keyCell.setPaddingRight(3f);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, REGULAR.at(9.2f, INK)));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(DIVIDER);
        valueCell.setBorderWidth(0.6f);
        valueCell.setPaddingTop(2.7f);
        valueCell.setPaddingBottom(2.7f);
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

    private void addStatValues(PdfPTable table, String... values) {

        for (String value : values) {

            PdfPCell cell = value.isEmpty() ? empty() : borderless();
            cell.setPaddingTop(1f);
            cell.setPaddingBottom(2f);

            if (!value.isEmpty()) {

                cell.addElement(new Paragraph(value, REGULAR.at(10.2f, INK)));
            }

            table.addCell(cell);
        }
    }

    private PdfPTable totalConsideration(BigDecimal total) {

        PdfPTable table = new PdfPTable(new float[] { 50f, 50f });
        table.setWidthPercentage(100f);

        /*
         * The total is the strongest element of the deal terms: a pale cyan
         * band, a cyan bar marking where it starts, and navy bold type a little
         * larger than the rows above it. The amount keeps right padding of its
         * own so a large figure stays inside the band rather than running off
         * the card.
         */
        PdfPCell label = new PdfPCell(new Phrase("Total consideration", BOLD.at(10.5f, NAVY)));
        label.setBackgroundColor(TOTAL_BG);
        label.setBorder(Rectangle.LEFT);
        label.setBorderColor(CYAN);
        label.setBorderWidthLeft(2.5f);
        label.setPaddingTop(8f);
        label.setPaddingBottom(8f);
        label.setPaddingLeft(9f);
        label.setPaddingRight(6f);

        Phrase amount = new Phrase();
        amount.add(new Chunk(CURRENCY, BOLD.at(9.5f, NAVY_SOFT)));
        amount.add(new Chunk(money(total), BOLD.at(11.5f, NAVY)));

        PdfPCell value = new PdfPCell(amount);
        value.setBackgroundColor(TOTAL_BG);
        value.setBorder(Rectangle.NO_BORDER);
        value.setPaddingTop(8f);
        value.setPaddingBottom(8f);
        value.setPaddingLeft(6f);
        value.setPaddingRight(9f);
        value.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(label);
        table.addCell(value);

        return table;
    }

    private PdfPCell signatureText(String text) {

        PdfPCell cell = borderless();
        cell.setPaddingTop(2f);

        Paragraph paragraph = new Paragraph(text, BOLD.at(9.2f, NAVY));
        paragraph.setSpacingBefore(0f);

        cell.addElement(paragraph);

        return cell;
    }

    /** Clear, unbordered space in which a signature is written by hand. */
    private PdfPCell signatureSpace() {

        PdfPCell cell = new PdfPCell(new Phrase(" ", FontFactory.getFont(
                FontFactory.HELVETICA, 1f)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(34f);

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
     * Fills a card with its panel colour and strokes its hairline outline, both
     * as one rounded rectangle.
     *
     * <p>Drawing on the background canvas rather than the cell's fill is what
     * makes the corners genuinely rounded: the page behind the card — its wash,
     * rings and watermark — shows through the four arcs instead of being painted
     * over with a white square.</p>
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

    /**
     * Paints the page furniture: the white page, the subtle corner rings, the
     * Click4Bonds watermark, the masthead and the address bar.
     *
     * <p>The background pieces are drawn on the under-content canvas so the
     * letter's text and cards sit on top of them; the masthead and footer are
     * drawn on the normal canvas in the margins the document reserves for them,
     * so they never overlap the body.</p>
     */
    private static final class LetterheadAndFooter extends PdfPageEventHelper {

        private static final String WATERMARK_TEXT = "Click4Bonds";

        private final Image logo;
        private final float contentWidth;

        private LetterheadAndFooter(Image logo, float contentWidth) {

            this.logo = logo;
            this.contentWidth = contentWidth;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {

            Rectangle page = document.getPageSize();
            float left = document.leftMargin();

            drawBackground(writer.getDirectContentUnder(), page);

            PdfContentByte canvas = writer.getDirectContent();
            float cursor = page.getTop() - HEADER_TOP_GAP;

            if (logo != null) {

                float height = logo.getScaledHeight();

                canvas.addImage(logo, contentWidth, 0, 0, height, left, cursor - height);

                cursor -= height + MASTHEAD_BAND_GAP;
            }

            /*
             * A navy rule with a cyan tail: the one strong horizontal accent in
             * the design, sitting directly below the masthead image.
             */
            float cyanWidth = contentWidth * 0.28f;

            canvas.setColorFill(NAVY);
            canvas.rectangle(
                    left, cursor - BAND_HEIGHT, contentWidth - cyanWidth, BAND_HEIGHT);
            canvas.fill();

            canvas.setColorFill(CYAN);
            canvas.rectangle(
                    left + contentWidth - cyanWidth,
                    cursor - BAND_HEIGHT,
                    cyanWidth,
                    BAND_HEIGHT);
            canvas.fill();

            drawFooter(canvas, page);
        }

        /** White page, two pale blue washes, corner rings and the watermark. */
        private static void drawBackground(PdfContentByte canvas, Rectangle page) {

            float width = page.getWidth();
            float height = page.getHeight();

            canvas.saveState();

            canvas.setColorFill(WHITE);
            canvas.rectangle(0f, 0f, width, height);
            canvas.fill();

            /*
             * A wash under the masthead and another rising from the address
             * bar, each fading to white before it reaches the body text. Drawn
             * as hairline bands rather than a PDF shading pattern, so the
             * letter needs no shading extension and every viewer agrees.
             */
            wash(canvas, width, height * 0.58f, height * 0.42f, WHITE, PAGE_TOP_TINT);
            wash(canvas, width, 0f, height * 0.24f, PAGE_BOTTOM_TINT, WHITE);

            canvas.setColorStroke(CORNER_PATTERN);
            canvas.setLineWidth(0.9f);

            for (float radius = 46f; radius <= 150f; radius += 13f) {

                canvas.circle(width + 4f, height + 4f, radius);
                canvas.stroke();

                canvas.circle(-4f, -4f, radius);
                canvas.stroke();
            }

            canvas.restoreState();

            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_CENTER,
                    new Phrase(WATERMARK_TEXT, FontFactory.getFont(
                            FontFactory.HELVETICA_BOLD, 62f, Font.NORMAL, WATERMARK)),
                    width / 2f,
                    height * 0.30f,
                    32f);
        }

        /**
         * Paints a vertical blue wash as a stack of hairline bands.
         *
         * <p>{@code bottom} is where the wash begins and {@code height} how far
         * it rises; {@code from} is the colour at the bottom and {@code to} the
         * colour at the top. The 0.4f overlap on each band closes the hairline
         * seam that antialiasing would otherwise leave between fills.</p>
         */
        private static void wash(
                PdfContentByte canvas, float width,
                float bottom, float height, Color from, Color to) {

            int bands = 64;
            float bandHeight = height / bands;

            for (int band = 0; band < bands; band++) {

                canvas.setColorFill(blend(from, to, (band + 0.5f) / bands));
                canvas.rectangle(
                        0f, bottom + band * bandHeight, width, bandHeight + 0.4f);
                canvas.fill();
            }
        }

        /** A colour part of the way from {@code from} to {@code to}. */
        private static Color blend(Color from, Color to, float ratio) {

            return new Color(
                    Math.round(from.getRed() + (to.getRed() - from.getRed()) * ratio),
                    Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * ratio),
                    Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * ratio));
        }

        /** The address bar: a pale blue band under a navy/cyan rule. */
        private static void drawFooter(PdfContentByte canvas, Rectangle page) {

            float width = page.getWidth();

            canvas.setColorFill(FOOTER_BG);
            canvas.rectangle(0f, FOOTER_BOTTOM_GAP, width, FOOTER_HEIGHT);
            canvas.fill();

            canvas.setColorFill(NAVY);
            canvas.rectangle(0f, FOOTER_BOTTOM_GAP + FOOTER_HEIGHT - 1f, width, 1f);
            canvas.fill();

            canvas.setColorFill(CYAN);
            canvas.rectangle(
                    0f, FOOTER_BOTTOM_GAP + FOOTER_HEIGHT - 1f, width * 0.28f, 1f);
            canvas.fill();

            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_CENTER,
                    new Phrase(FOOTER_LINE, REGULAR.at(7.5f, FOOTER_TEXT)),
                    width / 2f,
                    FOOTER_BOTTOM_GAP + (FOOTER_HEIGHT - 7.5f) / 2f + 1f,
                    0f);
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

    /**
     * The bond's put/call description as stored, which may be a date, "NA" or
     * other free text. An absent one prints as {@code N.A} rather than the
     * letter's usual em dash: the deal term is "not applicable", not "unknown".
     */
    private static String putCall(String value) {

        return value == null || value.isBlank() ? "N.A" : value;
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

    /**
     * Loads the masthead and scales it to the content width.
     *
     * @return the image, or null when the resource is missing or will not decode
     */
    private static Image letterheadImage(float width) {

        byte[] bytes = letterhead();

        if (bytes == null) {

            return null;
        }

        try {

            Image image = Image.getInstance(bytes);

            image.scaleToFit(width, width * image.getHeight() / image.getWidth());

            return image;

        } catch (DocumentException | IOException failure) {

            /*
             * A masthead that will not decode is not worth withholding a
             * confirmation over; the rest of the letter identifies the issuer.
             */
            log.warn("Could not place {}, the letter continues without it", LETTERHEAD_RESOURCE, failure);

            return null;
        }
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

    /**
     * One family and weight of a base-14 typeface.
     *
     * <p>Using a base-14 font means every PDF reader already has the glyphs, so
     * the letter carries no font file and renders the same on every machine.</p>
     */
    private static final class Typeface {

        private final String family;
        private final int style;

        private Typeface(String family, int style) {

            this.family = family;
            this.style = style;
        }

        Font at(float size, Color color) {

            return FontFactory.getFont(family, size, style, color);
        }
    }
}
