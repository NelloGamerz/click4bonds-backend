package com.click4bonds.app.Modules.DealConfirmation.Service;

/**
 * Which confirmation letter a deal is printed on.
 *
 * <p>Both letters share one design and one renderer; the layout decides which
 * figures and particulars the letter carries — stamp duty and clearing-house
 * details on the corporate letter, TDS and bank particulars on the G-Sec one.</p>
 */
public enum DealConfirmationLayout {

    CORPORATE,

    GSEC
}
