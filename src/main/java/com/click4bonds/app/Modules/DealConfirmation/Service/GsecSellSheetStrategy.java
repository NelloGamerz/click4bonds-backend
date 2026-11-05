package com.click4bonds.app.Modules.DealConfirmation.Service;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

/**
 * The G-Sec letter: the layout a Sovereign-rated bond is confirmed on.
 *
 * <p>Reached only for a bond whose rating is {@code Sovereign} — see
 * {@link DealConfirmationSheetStrategyFactory}. Two differences are worth
 * knowing:</p>
 *
 * <ul>
 *   <li><strong>TDS instead of stamp duty.</strong> The corporate letter adds
 *       stamp duty to reach its total; this one <em>deducts</em> TDS. Both
 *       figures are computed in {@link DealConfirmationSheetValuesFactory}, so
 *       neither charge's arithmetic lives in a layout class.</li>
 *   <li><strong>A different particulars block.</strong> Bank-side particulars
 *       rather than the clearing corporation's.</li>
 * </ul>
 *
 * <p><strong>One thing on this letter is not settled.</strong> The TDS label on
 * the old sheet still read "0.10%" while the figure printed beside it is 10% of
 * the accrued interest — the agreed rate, and roughly three times what the
 * label's own formula would have produced. The value is right and the wording is
 * not; that is a finance question, recorded in
 * {@link DealConfirmationSheetValues} and in {@code docs/deal-confirmation.md}.</p>
 */
@Component
public class GsecSellSheetStrategy implements DealConfirmationSheetStrategy {

    @Override
    public DealConfirmationLayout layout() {

        return DealConfirmationLayout.GSEC;
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
    @Override
    public void requireFigures(DealConfirmationSheetValues values) {

        if (values.gsecAccruedDays() == null
                || values.gsecAccruedInterest() == null
                || values.gsecConsiderationAmount() == null
                || values.gsecTds() == null
                || values.gsecTotalConsiderationAmount() == null) {

            throw new DocumentGenerationException(
                    "Deal " + values.dealReference()
                            + " has no maturity date or no coupon rate, so the G-Sec"
                            + " accrued interest cannot be computed and no"
                            + " confirmed letter can be printed for it. A Sovereign"
                            + " bond should have both.");
        }
    }
}
