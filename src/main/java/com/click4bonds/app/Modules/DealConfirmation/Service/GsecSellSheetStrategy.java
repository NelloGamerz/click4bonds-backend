package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

import lombok.RequiredArgsConstructor;

/**
 * The G-Sec sell sheet: the letter a Sovereign-rated bond is confirmed on.
 *
 * <p>Reached only for a bond whose rating is {@code Sovereign} — see
 * {@link DealConfirmationSheetStrategyFactory}. The sheet is not the corporate
 * layout moved sideways, and two differences are worth knowing before reading the
 * cell map:</p>
 *
 * <ul>
 *   <li><strong>TDS instead of stamp duty.</strong> The corporate sheet adds stamp
 *       duty to reach its total; this sheet <em>deducts</em> TDS. Both figures are
 *       computed in {@link DealConfirmationSheetValuesFactory}, so neither
 *       charge's arithmetic lives in a layout class — this strategy only decides
 *       which cell each one goes in.</li>
 *   <li><strong>A different particulars block.</strong> Rows 31-36 ask for the
 *       beneficiary, PAN, branch, bank, bank IFSC and account number. The IFSC
 *       here is the <em>bank's</em>, not the clearing corporation's
 *       {@code ifscCode} the corporate sheet prints — see
 *       {@code DocumentProperties.Organisation}.</li>
 * </ul>
 *
 * <p>Everything the customer supplies — "Your PAN", "Your Dp Name", "DP ID",
 * "CLIENT ID" in rows 32-35 — is deliberately absent from the map, and the bank
 * particulars are written even when configured blank, so the sample values the
 * template was saved with cannot survive the fill. See the map's own comments.</p>
 *
 * <p><strong>One thing on this letter is not settled.</strong> The TDS cell's
 * label still reads "0.10%" while the figure beside it is 10% of the accrued
 * interest — the agreed rate, and roughly three times what the cell's own formula
 * would have produced. The value is right and the wording is not; that is a
 * finance question, recorded in {@link DealConfirmationSheetValues} and in
 * {@code docs/deal-confirmation.md}.</p>
 */
@Component
@RequiredArgsConstructor
public class GsecSellSheetStrategy implements DealConfirmationSheetStrategy {

    private final DocumentProperties documentProperties;

    @Override
    public String sheetName() {

        return documentProperties.getTemplate().getGsecSheetName();
    }

    /**
     * The G-Sec letter's layout.
     *
     * <p>Same label/value shape as the corporate sheet, different rows: the deal
     * terms run down rows 12-24 and the money down 25-30, all in column C, and our
     * particulars block occupies A/B in rows 32-36.</p>
     *
     * @param values the letter's values
     * @return cell address to value, in sheet order
     */
    @Override
    public Map<String, Object> toCells(DealConfirmationSheetValues values) {

        requireGsecFigures(values);

        DocumentProperties.Organisation organisation =
                documentProperties.getOrganisation();

        Map<String, Object> cells = new LinkedHashMap<>();

        // ---- Letterhead ----------------------------------------------------
        cells.put("A4", values.letterDate());
        cells.put("A5", values.dealReference());

        /*
         * Merged across A7:D7. C41 holds a live echo of this cell, overwritten
         * for the same reason as the corporate sheet's C39: left alone it would
         * reproduce whichever counterparty the template was last saved with.
         */
        cells.put("A7", values.counterpartyLine());
        cells.put("C41", values.counterpartyLine());

        // ---- Deal terms ----------------------------------------------------
        cells.put("C12", values.transactionType());

        /*
         * Demat, not the corporate letter's ICCL — a G-Sec settles in demat form.
         * The template's own sample value is "Demat ", which is the only place
         * the convention was written down.
         */
        cells.put("C13", organisation.getGsecModeOfDelivery());

        cells.put("C14", values.dealDate());
        cells.put("C15", values.valueDate());
        cells.put("C16", values.isin());
        cells.put("C17", values.price());
        cells.put("C18", values.securityName());
        cells.put("C19", values.maturityDate());
        cells.put("C20", values.ipDateDescription());
        cells.put("C21", values.lastInterestPaymentDate());

        /*
         * The coupon goes in as a FRACTION — 0.0734 — because that is what the
         * sheet's own format and its sample data use, and what its interest formula
         * multiplies by. The corporate sheet writes the percentage 7.34 under a
         * replaced format instead; the two differ on purpose, and swapping them
         * would be wrong by a factor of a hundred.
         *
         * Written as a double rather than a BigDecimal for a reason worth knowing:
         * XlsxTemplateWriter rounds every BigDecimal to two decimals, which is
         * right for money and would turn 0.0734 into 0.07, printing "7.00%".
         */
        cells.put("C22", couponFraction(values));
        cells.put("C23", values.gsecAccruedDays());
        cells.put("C24", values.numberOfBonds());

        // ---- Money ---------------------------------------------------------
        /*
         * Every one of these cells holds a formula in the template, and every one
         * is overwritten with a literal. Two reasons, and they are different:
         *
         *   C29's formula charges 0.1% of the CONSIDERATION where the agreed rule
         *   is 10% of the INTEREST — the wrong rule, not a rounding of the right
         *   one. It has to be replaced.
         *
         *   The rest are replaced because a formula left in the sheet carries the
         *   cached result from whichever deal the template was saved with, and
         *   nothing guarantees the application that opens it recalculates before
         *   rendering. C23's days and C27's interest ARE this sheet's convention
         *   and are reproduced by GsecAccrualCalculator; they are written as
         *   literals so the printed figure cannot depend on the renderer.
         *
         * C27 is the G-SEC accrued interest, not the corporate one: quantum x
         * coupon x days / 360. TDS comes OFF here, where the corporate sheet's
         * stamp duty goes ON.
         */
        cells.put("C25", values.quantum());
        cells.put("C26", values.principalAmount());
        cells.put("C27", values.gsecAccruedInterest());
        cells.put("C28", values.gsecConsiderationAmount());
        cells.put("C29", values.gsecTds());
        cells.put("C30", values.gsecTotalConsiderationAmount());

        // ---- Our particulars (rows 31-36, values in B and C) ---------------
        cells.put("C31", organisation.getBeneficiaryName());

        cells.put("B32", organisation.getPan());

        /*
         * Written even when blank, so the template's sample branch, bank and
         * account details are cleared rather than printed. See
         * DocumentProperties.Organisation for why they default to nothing.
         */
        cells.put("B33", organisation.getBranchLocation());
        cells.put("B34", organisation.getBankName());
        cells.put("B35", organisation.getBankIfsc());
        cells.put("B36", organisation.getAccountNumber());

        /*
         * D32:D35 are the customer's own identifiers — "Your PAN", "Your Dp
         * Name", "DP ID", "CLIENT ID" sit in column C as labels and their values
         * belong in column D. This application holds none of them, so they are
         * not in the map at all and the letter leaves them for the customer, as
         * the corporate sheet does.
         */

        return cells;
    }

    /**
     * No format overrides.
     *
     * <p>The corporate sheet has to replace its coupon cell's format because that
     * sheet prints the rate as {@code Bond} stores it, a percentage, under a format
     * meant for a fraction. This sheet does it the other way round: the coupon goes
     * in as a fraction, which is what the cell's own {@code 0.00%} format expects
     * and what the sheet's sample data holds. {@code 0.0734} under it prints
     * {@code 7.34%}, with nothing to override.</p>
     *
     * <p>The inherited empty default is therefore the whole answer here, and the
     * override that used to be on this method was wrong for this sheet.</p>
     */
    @Override
    public Map<String, String> numberFormats() {

        return Map.of();
    }

    /**
     * The coupon as a fraction, e.g. {@code 0.0734} for 7.34%.
     *
     * <p>Returned as a {@code double}, not a {@code BigDecimal}, because
     * {@code XlsxTemplateWriter} reduces every BigDecimal to two decimals — correct
     * for money, and fatal here: {@code 0.0734} would become {@code 0.07} and the
     * letter would read "7.00%". The writer's {@code Number} branch writes the value
     * through untouched.</p>
     *
     * <p>Null when the deal has no coupon rate, which
     * {@link #requireGsecFigures} has already refused by the time this is called,
     * so the null only guards the compiler.</p>
     */
    private Double couponFraction(DealConfirmationSheetValues values) {

        return values.couponRate() == null
                ? null
                : values.couponRate().movePointLeft(2).doubleValue();
    }

    /**
     * Refuses a deal whose G-Sec figures could not be computed.
     *
     * <p>The values factory returns them as null rather than throwing, because a
     * perpetual bond with no maturity and a deal with no coupon rate are both
     * legitimate for the corporate letter. A government security has both, so the
     * absence is only a problem here — and it is a refusal rather than a blank
     * letter, because accrued interest is the figure the consideration is built
     * from and a letter without it is not a confirmation of anything.</p>
     *
     * <p>The deal stays {@code CREATED} with no document recorded, which is the
     * module's existing failure mode: the purchase stands and the letter is
     * retryable.</p>
     */
    private void requireGsecFigures(DealConfirmationSheetValues values) {

        if (values.gsecAccruedDays() == null
                || values.gsecAccruedInterest() == null
                || values.gsecConsiderationAmount() == null
                || values.gsecTds() == null
                || values.gsecTotalConsiderationAmount() == null) {

            throw new DocumentGenerationException(
                    "Deal " + values.dealReference()
                            + " has no maturity date or no coupon rate, so the G-Sec"
                            + " sheet's accrued interest cannot be computed and no"
                            + " confirmed letter can be printed for it. A Sovereign"
                            + " bond should have both.");
        }
    }
}
