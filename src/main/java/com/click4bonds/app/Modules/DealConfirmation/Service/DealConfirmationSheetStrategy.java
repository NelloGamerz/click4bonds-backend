package com.click4bonds.app.Modules.DealConfirmation.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;

/**
 * The layout of one deal confirmation letter.
 *
 * <p>A corporate bond sale and a G-Sec sale do not print the same fields, and
 * the G-Sec letter charges TDS where the corporate one charges stamp duty. Those
 * differences are a whole layout, so they are a strategy rather than a branch
 * inside the renderer.</p>
 *
 * <p><strong>The arithmetic is not here.</strong> All of it lives in
 * {@link DealConfirmationSheetValuesFactory}, which produces the same
 * {@link DealConfirmationSheetValues} for every strategy. A strategy decides
 * which figures the letter prints, never what they are — which is what keeps two
 * layouts from drifting into two definitions of accrued interest.</p>
 *
 * <p>{@link DealConfirmationSheetStrategyFactory} decides which of these a given
 * deal gets; nothing else should name an implementation.</p>
 */
public interface DealConfirmationSheetStrategy {

    /**
     * Which letter this layout draws.
     *
     * <p>Both letters share one PDF renderer, so it needs to know which figures
     * and particulars to print.</p>
     */
    default DealConfirmationLayout layout() {

        return DealConfirmationLayout.CORPORATE;
    }

    /**
     * Refuses a deal whose figures this layout cannot print.
     *
     * <p>Empty by default. A layout that requires figures the shared values may
     * lack overrides this to refuse before a blank letter is drawn.</p>
     */
    default void requireFigures(DealConfirmationSheetValues values) {
    }
}
