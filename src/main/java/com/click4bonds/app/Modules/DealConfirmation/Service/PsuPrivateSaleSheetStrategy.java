package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;

import lombok.RequiredArgsConstructor;

/**
 * Maps confirmation letter values onto cells of the {@code PSU Private Sale }
 * sheet — the corporate bond letter, and the layout every deal gets unless the
 * bond is a Sovereign.
 *
 * <p>The whole layout of the letter in one place, so a template revision is a
 * change to one table rather than a hunt through the codebase. Addresses are
 * pinned by a test against the real template.</p>
 *
 * <p>The sheet is laid out as label/value pairs: rows 12 to 29 put the label in
 * column A and the value in column C, and rows 30 to 34 put one pair in A/B and
 * another in C/D. Only values are written — never labels — so the letter's
 * wording stays the template's business.</p>
 *
 * <p>Cells the customer fills in by hand are deliberately absent. The letter
 * prints "Your PAN", "DP ID", "CLIENT ID" and "Your Dp Name" with nothing after
 * them, because this application holds none of those values and inventing a
 * default would put a wrong identifier on a legal document.</p>
 *
 * <p>This class computes nothing. The arithmetic is
 * {@link DealConfirmationSheetValuesFactory}'s and is shared with every other
 * layout — see {@link DealConfirmationSheetStrategy}.</p>
 */
@Component
@RequiredArgsConstructor
public class PsuPrivateSaleSheetStrategy implements DealConfirmationSheetStrategy {

    private final DocumentProperties documentProperties;

    @Override
    public String sheetName() {

        return documentProperties.getTemplate().getSheetName();
    }

    /**
     * @param values the letter's values
     * @return cell address to value, in sheet order
     */
    @Override
    public Map<String, Object> toCells(DealConfirmationSheetValues values) {

        DocumentProperties.Organisation organisation =
                documentProperties.getOrganisation();

        Map<String, Object> cells = new LinkedHashMap<>();

        // ---- Letterhead ----------------------------------------------------
        cells.put("A4", values.letterDate());
        cells.put("A5", values.dealReference());

        /*
         * Merged across A7:D7. The template ships a live echo of this cell in
         * C39, which is overwritten too — a formula left behind would reproduce
         * whichever counterparty the template was last saved with.
         */
        cells.put("A7", values.counterpartyLine());
        cells.put("C39", values.counterpartyLine());

        // ---- Deal terms ----------------------------------------------------
        cells.put("C12", values.transactionType());
        cells.put("C13", values.modeOfDelivery());
        cells.put("C14", values.dealDate());
        cells.put("C15", values.valueDate());
        cells.put("C16", values.isin());
        cells.put("C17", values.price());
        cells.put("C18", values.securityName());
        cells.put("C19", values.maturityDate());
        cells.put("C20", values.ipDateDescription());
        cells.put("C21", values.lastInterestPaymentDate());
        cells.put("C22", values.couponRate());
        cells.put("C23", values.accruedDays());
        cells.put("C24", values.numberOfBonds());

        // ---- Money ---------------------------------------------------------
        /*
         * Every one of these cells holds a formula in the template, using
         * day-count conventions that disagree with this application's. They are
         * overwritten with the figures the platform actually charged.
         */
        cells.put("C25", values.quantum());
        cells.put("C26", values.principalAmount());
        cells.put("C27", values.accruedInterest());
        cells.put("C28", values.stampDuty());
        cells.put("C29", values.totalConsideration());

        // ---- Our particulars (rows 30-34, columns A/B then C/D) ------------
        cells.put("B30", organisation.getPan());
        cells.put("B31", organisation.getCmBpId());
        cells.put("D31", organisation.getIfscCode());
        cells.put("B32", organisation.getCmName());
        cells.put("D32", organisation.getBeneficiaryName());
        cells.put("B33", organisation.getMarketType());
        cells.put("D33", organisation.getAccountNumber());
        cells.put("B34", organisation.getSettlementNumber());
        cells.put("D34", organisation.getBanker());

        return cells;
    }

    /**
     * The cells whose template number format does not fit the value written to
     * them.
     *
     * <p>Only the coupon needs one. Its cell is formatted {@code 0.00%}, which
     * prints our value correctly only if a fraction is written to it — but the
     * letter prints the rate exactly as {@code Bond} stores it, a percentage, so
     * {@code 8.80} written under that format would read {@code 880.00%}.</p>*
     * <p>The replacement is {@code 0.00"%"}: two decimals followed by a percent
     * sign, with the sign <em>quoted</em> so Excel prints it as a literal rather
     * than as the fraction multiplier. The cell reads {@code 8.80%}, which is
     * what the number means, and the value written stays the percentage
     * {@code 8.80} rather than being divided by a hundred to suit the
     * template.</p>
     *
     * <p>Kept here, beside the addresses, because it is a fact about the
     * template's layout rather than about any value.</p>
     *
     * @return cell address to Excel number format, for the addresses that need
     *         one
     */
    @Override
    public Map<String, String> numberFormats() {

        return Map.of("C22", "0.00\"%\"");
    }
}
