package com.click4bonds.app.Modules.Bond.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowEntry;
import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowResponse;
import com.click4bonds.app.Modules.Bond.Enums.BondCashFlowType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.InternalServerException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * Renders the cash-flow projection as a PDF statement.
 *
 * <p>
 * The document mirrors the layout of the platform's existing cash-flow
 * statement — a letterhead, a title naming the ISIN, a bond-details block, an
 * investment block, the dated schedule and the disclaimer — so a reader who has
 * seen one recognises the other.
 *
 * <p>
 * The statement is built from the projection itself rather than from a
 * spreadsheet template: every figure below is read from
 * {@link BondCashFlowResponse}, so the PDF and the JSON endpoint can never
 * disagree about what the bond pays.
 *
 * <p>
 * The schedule can run to hundreds of rows. It is added as a single
 * {@link PdfPTable} whose header row is marked as repeating, so the column
 * headings reappear on every page and the table splits cleanly.
 */
@Service
@Slf4j
public class BondCashFlowPdfServiceImpl implements BondCashFlowPdfService {

    /**
     * The trading name, and the legal entity behind it. The statement is issued
     * under the brand, with the registered company and its address beneath it.
     */
    private static final String BRAND_NAME = "CLICK4BOND";
    private static final String LEGAL_NAME = "AllTime Securities Pvt Ltd";
    private static final String SUBSIDIARY_NOTE =
            "Click4Bond is a subsidiary of AllTime Securities Pvt Ltd";
    private static final String ADDRESS_LINE_1 =
            "Plot No. 83-84, First Floor, Pocket D-14, Shiva Road,";
    private static final String ADDRESS_LINE_2 =
            "Sector 8, Rohini, New Delhi – 110085, India";

    private static final Color BRAND = new Color(0x0B, 0x4F, 0x9E);
    private static final Color HEADER_FILL = new Color(0x0B, 0x4F, 0x9E);
    private static final Color KEY_FILL = new Color(0xEE, 0xF2, 0xF8);
    private static final Color ROW_FILL = new Color(0xF7, 0xF9, 0xFC);
    private static final Color GRID = new Color(0xC8, 0xD2, 0xDE);

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("d-MMM-yyyy hh:mm:ss a", Locale.ENGLISH);

    /*
     * Amounts are printed with a thousands separator and two decimals. Negative
     * figures — only the purchase leg — keep a leading minus rather than the
     * accounting parentheses, so the one outflow reads unambiguously.
     */
    private static final ThreadLocal<DecimalFormat> AMOUNT_FORMAT = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    private static final ThreadLocal<DecimalFormat> QUANTITY_FORMAT = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    private static final ThreadLocal<DecimalFormat> RATE_FORMAT = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.0000", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    private static final String[] SCHEDULE_HEADERS = {
            "Date",
            "Coupon Amount (Rs)",
            "Principal Amount (Rs)",
            "Total Amount (Rs)",
            "Outstanding Principal (Rs)",
            "Remarks"
    };

    private static final float[] SCHEDULE_WIDTHS = { 13f, 17f, 17f, 17f, 21f, 15f };

    @Override
    public byte[] render(Bond bond, BondCashFlowResponse cashFlow) {

        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }
        if (cashFlow == null) {
            throw new IllegalArgumentException("Cash flow cannot be null");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4, 36, 36, 54, 60);

        // Set before open(), so the properties land in the document catalog.
        document.addTitle("Cashflow Statement - " + cashFlow.isin());
        document.addAuthor(LEGAL_NAME);
        document.addCreator(BRAND_NAME);

        try {

            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer(LocalDateTime.now()));

            document.open();

            addLetterhead(document);
            addTitle(document, cashFlow.isin());
            addBondDetails(document, bond, cashFlow);
            addInvestmentDetails(document, cashFlow);
            addSchedule(document, cashFlow.schedule());
            addDisclaimer(document);

            document.close();

        } catch (DocumentException failure) {

            log.error(
                    "Failed to render the cash flow PDF for isin={}",
                    cashFlow.isin(),
                    failure);

            throw new InternalServerException(
                    "Could not generate the cash flow PDF for " + cashFlow.isin(),
                    failure);

        } finally {

            /*
             * A close() after the explicit one is a no-op, but the guard keeps
             * a failure part way through from leaving the writer's buffers
             * unflushed while a later close() tries to finish the document.
             */
            if (document.isOpen()) {
                document.close();
            }
        }

        return out.toByteArray();
    }

    @Override
    public String fileName(Bond bond) {

        String isin = bond == null ? null : bond.getIsin();

        String safe = isin == null || isin.isBlank()
                ? "bond"
                : isin.replaceAll("[^A-Za-z0-9._-]", "_");

        return "Click4Bond-Cashflow-" + safe + ".pdf";
    }

    private void addLetterhead(Document document) throws DocumentException {

        Paragraph brand = new Paragraph(
                BRAND_NAME,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.BOLD, BRAND));
        brand.setAlignment(Element.ALIGN_CENTER);
        brand.setSpacingAfter(2f);
        document.add(brand);

        Paragraph legal = new Paragraph(
                LEGAL_NAME,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.DARK_GRAY));
        legal.setAlignment(Element.ALIGN_CENTER);
        legal.setSpacingAfter(1f);
        document.add(legal);

        document.add(centred(ADDRESS_LINE_1, 9));
        document.add(centred(ADDRESS_LINE_2, 9));
        document.add(centred(SUBSIDIARY_NOTE, 8));
    }

    private Paragraph centred(String text, int size) {

        Paragraph paragraph = new Paragraph(
                text,
                FontFactory.getFont(FontFactory.HELVETICA, size, Font.NORMAL, Color.GRAY));

        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(1f);

        return paragraph;
    }

    private void addTitle(Document document, String isin) throws DocumentException {

        Paragraph title = new Paragraph(
                "CASHFLOW STATEMENT FOR ISIN: " + isin,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(12f);
        title.setSpacingAfter(12f);
        document.add(title);
    }

    private void addBondDetails(
            Document document,
            Bond bond,
            BondCashFlowResponse cashFlow) throws DocumentException {

        document.add(sectionTitle("BOND DETAILS"));

        PdfPTable table = detailsTable();

        addDetailRow(table, "Issuer Name", cashFlow.bondName() != null
                ? cashFlow.bondName()
                : bond.getName());
        addDetailRow(table, "Credit Rating", rating(bond));
        addDetailRow(table, "Payment Terms", nameOf(bond.getCouponFrequency()));
        addDetailRow(table, "Coupon", percent(bond.getCouponRate()));
        addDetailRow(table, "YTM", percent(bond.getAnnualYtm()));
        addDetailRow(table, "Maturity Date", date(bond.getMaturityDate()));
        addDetailRow(table, "Security", nameOf(bond.getSecurityType()));
//        addDetailRow(table, "Category", bond.getCategory());

        document.add(table);
    }

    private void addInvestmentDetails(
            Document document,
            BondCashFlowResponse cashFlow) throws DocumentException {

        document.add(sectionTitle("INVESTMENT DETAILS"));

        PdfPTable table = detailsTable();

        addDetailRow(table, "Units Selected", quantity(cashFlow.totalBond()) + " Unit(s)");
        addDetailRow(table, "Settlement Date", date(cashFlow.calculationDate()));
        addDetailRow(table, "Purchase Consideration (Rs)", amount(cashFlow.purchaseConsideration()));
        addDetailRow(table, "Total Coupon (Rs)", amount(cashFlow.totalCoupon()));
        addDetailRow(table, "Total Principal (Rs)", amount(cashFlow.totalPrincipal()));
        addDetailRow(table, "Total Cash Flow (Rs)", amount(cashFlow.totalCashFlow()));

        document.add(table);
    }

    private void addSchedule(Document document, List<BondCashFlowEntry> schedule)
            throws DocumentException {

        document.add(sectionTitle("CASHFLOW SCHEDULE"));

        PdfPTable table = new PdfPTable(SCHEDULE_HEADERS.length);
        table.setWidthPercentage(100f);
        table.setWidths(SCHEDULE_WIDTHS);
        table.setSpacingBefore(2f);

        for (String header : SCHEDULE_HEADERS) {
            table.addCell(scheduleHeaderCell(header));
        }

        /*
         * Repeats the heading row on every page the table spills onto. Without
         * it a reader on page three sees columns of figures with nothing to
         * say which is which.
         */
        table.setHeaderRows(1);

        List<BondCashFlowEntry> entries = schedule == null ? List.of() : schedule;

        for (int index = 0; index < entries.size(); index++) {

            BondCashFlowEntry entry = entries.get(index);
            Color fill = index % 2 == 0 ? Color.WHITE : ROW_FILL;

            table.addCell(scheduleCell(date(entry.date()), Element.ALIGN_LEFT, fill));
            table.addCell(scheduleCell(amount(entry.couponAmount()), Element.ALIGN_RIGHT, fill));
            table.addCell(scheduleCell(amount(entry.principalAmount()), Element.ALIGN_RIGHT, fill));
            table.addCell(scheduleCell(amount(entry.amount()), Element.ALIGN_RIGHT, fill));
            table.addCell(scheduleCell(amount(entry.outstandingPrincipal()), Element.ALIGN_RIGHT, fill));
            table.addCell(scheduleCell(remarks(entry.type()), Element.ALIGN_LEFT, fill));
        }

        document.add(table);
    }

    private void addDisclaimer(Document document) throws DocumentException {

        document.add(sectionTitle("DISCLAIMER"));

        for (String clause : DISCLAIMER) {
            Paragraph paragraph = new Paragraph(
                    clause,
                    FontFactory.getFont(FontFactory.HELVETICA, 7, Font.NORMAL, Color.DARK_GRAY));
            paragraph.setSpacingAfter(3f);
            document.add(paragraph);
        }
    }

    /**
     * The statement's standing disclaimer, carried over from the platform's
     * template with the issuing entity corrected to the one that issues this
     * document.
     */
    private static final List<String> DISCLAIMER = List.of(
            "1. The actual date of interest payment and the amount receivable may vary depending upon"
                    + " bank holidays, weekends, or any other operational factors at the Issuer's end."
                    + " The interest rate and amount indicated herein are for illustrative and"
                    + " informational purposes only. Investors are advised to refer to their bank"
                    + " statement or the official communication from the Issuer for the exact interest"
                    + " amount and payment date.",
            "2. Tax Deducted at Source (TDS) shall be deducted on the interest payable as per the"
                    + " applicable provisions of the Income Tax Act, 1961. Investors can view their TDS"
                    + " details in Form 26AS. TDS credit, if any, can be claimed while filing the Income"
                    + " Tax Return.",
            "3. In case the investor is eligible and has successfully submitted Form 121 to the"
                    + " respective Registrar / Issuer and the same has been accepted, TDS will not be"
                    + " deducted on interest payouts. For any query regarding the same, please reach out"
                    + " directly to the Issuer / RTA.",
            "4. The information and data contained in this communication are provided on an \"as is\""
                    + " basis without any representation or warranty of any kind, express or implied. We"
                    + " do not guarantee the accuracy, completeness, or timeliness of the content. We do"
                    + " not guarantee or warrant the settlement, execution, or successful completion of"
                    + " any trades conducted on the platform.",
            "5. The content of this communication is provided for informational purposes only and does"
                    + " not constitute an offer to invest or provide investment advice/recommendation or"
                    + " to avail of any service. Recipients of this communication are explicitly advised"
                    + " to conduct their own due diligence regarding their specific investment objectives"
                    + " and financial position and to seek independent advice as deemed necessary before"
                    + " making any investment decisions.",
            "6. The information, opinions, or views contained in this document are based on prevailing"
                    + " conditions and are subject to change without notice. The information provided is"
                    + " intended as a general guide and description of the products and should not be"
                    + " considered part of an offer or solicitation of an offer or contract.",
            "7. No information contained herein shall form the basis of or be part of any agreement,"
                    + " and no warranty, representation, or covenant is given or implied as to the"
                    + " accuracy or completeness of the whole or any part of this information. The"
                    + " information may be subject to updating, revision, and material changes.",
            "8. Prospective investors are strongly advised to conduct their own inquiries, perform due"
                    + " diligence, and satisfy themselves on all aspects of the product and other terms,"
                    + " including but not limited to pricing, features, risks, and financial and"
                    + " non-financial benefits, before making any investment decision at their absolute"
                    + " discretion.",
            "9. " + LEGAL_NAME + ", its directors, employees, affiliates, or representatives shall not be"
                    + " liable for any direct, indirect, incidental, consequential, or punitive damages,"
                    + " losses, or claims arising out of or in connection with the use of this information"
                    + " or any investment decision made based on it.",
            "10. Investments in securities are subject to risks including delay and/ or default in"
                    + " payment. Read all the offer related documents carefully. \"Investments in debt"
                    + " securities, municipal debt securities/ securitised debt instruments are subject"
                    + " to risks including delay and/ or default in payment. Read all the offer related"
                    + " documents carefully\".");

    private Paragraph sectionTitle(String text) {

        Paragraph paragraph = new Paragraph(
                text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, BRAND));

        paragraph.setSpacingBefore(10f);
        paragraph.setSpacingAfter(4f);

        return paragraph;
    }

    private PdfPTable detailsTable() {

        PdfPTable table = new PdfPTable(new float[] { 35f, 65f });
        table.setWidthPercentage(100f);
        table.setSpacingBefore(2f);

        return table;
    }

    private void addDetailRow(PdfPTable table, String key, String value) {

        PdfPCell keyCell = new PdfPCell(new Phrase(
                key,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD)));
        keyCell.setBackgroundColor(KEY_FILL);
        keyCell.setBorderColor(GRID);
        keyCell.setPadding(5f);

        PdfPCell valueCell = new PdfPCell(new Phrase(
                value == null ? "-" : value,
                FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL)));
        valueCell.setBorderColor(GRID);
        valueCell.setPadding(5f);

        table.addCell(keyCell);
        table.addCell(valueCell);
    }

    private PdfPCell scheduleHeaderCell(String text) {

        PdfPCell cell = new PdfPCell(new Phrase(
                text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(HEADER_FILL);
        cell.setBorderColor(GRID);
        cell.setPadding(4f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        return cell;
    }

    private PdfPCell scheduleCell(String text, int alignment, Color fill) {

        PdfPCell cell = new PdfPCell(new Phrase(
                text == null ? "" : text,
                FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL)));
        cell.setBackgroundColor(fill);
        cell.setBorderColor(GRID);
        cell.setPadding(3f);
        cell.setHorizontalAlignment(alignment);

        return cell;
    }

    /**
     * A readable label for a movement. The purchase leg is the one outflow, so
     * it is called out rather than left as an unlabelled negative.
     */
    private static String remarks(BondCashFlowType type) {

        if (type == null) {
            return "";
        }

        return switch (type) {
            case PURCHASE -> "Purchase consideration";
            case COUPON -> "Interest";
            case PRINCIPAL -> "Principal redemption";
            case COUPON_AND_PRINCIPAL -> "Interest + Principal";
        };
    }

    private static String rating(Bond bond) {

        if (bond.getRating() == null || bond.getRating().isBlank()) {
            return bond.getRatingAgency();
        }

        return bond.getRatingAgency() == null || bond.getRatingAgency().isBlank()
                ? bond.getRating()
                : bond.getRating() + " " + bond.getRatingAgency();
    }

    private static String percent(BigDecimal rate) {

        return rate == null ? null : RATE_FORMAT.get().format(rate) + "%";
    }

    private static String date(LocalDate value) {

        return value == null ? null : value.format(DATE);
    }

    private static String amount(BigDecimal value) {

        return value == null ? "" : AMOUNT_FORMAT.get().format(value);
    }

    private static String quantity(BigDecimal value) {

        return value == null ? "1" : QUANTITY_FORMAT.get().format(value);
    }

    private static String nameOf(Enum<?> value) {

        return value == null ? null : value.name();
    }

    /**
     * Stamps the footer on every page.
     *
     * <p>
     * Drawn in the page event rather than as content, because the page number is
     * only known once a page is finished — adding it as content would put the
     * first page's number out before the second page exists.
     */
    private static final class Footer extends PdfPageEventHelper {

        private final String generatedAt;

        private Footer(LocalDateTime generatedAt) {
            this.generatedAt = generatedAt.format(GENERATED_AT);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {

            PdfContentByte canvas = writer.getDirectContent();

            Phrase phrase = new Phrase(
                    "System generated report - Date: " + generatedAt
                            + "   Page " + writer.getPageNumber(),
                    FontFactory.getFont(FontFactory.HELVETICA, 7, Font.NORMAL, Color.GRAY));

            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_CENTER,
                    phrase,
                    (document.left() + document.right()) / 2,
                    document.bottom() - 20,
                    0);
        }
    }
}
