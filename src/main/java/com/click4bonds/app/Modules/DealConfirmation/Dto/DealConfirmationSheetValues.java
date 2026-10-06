package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The values a deal confirmation letter prints, independent of where they go.
 *
 * <p>Separate from the cell map so the arithmetic can be read and tested without
 * a spreadsheet address in sight, and so a template revision that moves a cell
 * does not touch the maths. Every money field is already rounded and in the unit
 * the letter shows — the coupling of quantity, face value and accrual happens
 * once, in the factory.</p>
 *
 * @param letterDate              date printed at the top of the letter
 * @param dealReference           our reference for the deal
 * @param counterpartyLine        the "Counterparty Name- ..." line
 * @param transactionType         e.g. "Our Sale"
 * @param modeOfDelivery          e.g. "ICCL"
 * @param dealDate                date the deal was struck
 * @param valueDate               settlement date
 * @param isin                    the bond's ISIN
 * @param price                   clean price per bond
 * @param securityName            the bond's name
 * @param maturityDate            the bond's maturity, or null when perpetual
 * @param ipDateDescription       the bond's raw interest-payment description
 * @param lastInterestPaymentDate last coupon date before the value date
 * @param couponRate              the bond's coupon as stored, a percentage:
 *                                8.80 prints as 8.80
 * @param accruedDays             days of interest accrued at settlement
 * @param numberOfBonds           quantity bought
 * @param quantum                 face value of the position
 * @param principalAmount         price value of the position
 * @param accruedInterest         interest component of the consideration
 * @param stampDuty               corporate sheet only. Stamp duty on the
 *                                consideration, whole rupees, <em>added</em>
 * @param totalConsideration      corporate sheet only: principal + accrued
 *                                interest + stamp duty
 * @param gsecAccruedDays         G-Sec sheet only. Days from the previous coupon
 *                                date, semi-annual on a European 30/360 count —
 *                                see {@code GsecAccrualCalculator}
 * @param gsecAccruedInterest     G-Sec sheet only: quantum × coupon ×
 *                                {@code gsecAccruedDays} / 360
 * @param gsecConsiderationAmount G-Sec sheet only: principal + that accrued
 *                                interest
 * @param gsecTds                 G-Sec sheet only: 10% of that accrued interest,
 *                                <em>deducted</em>
 * @param gsecTotalConsiderationAmount G-Sec sheet only: consideration − TDS
 *
 * <p><strong>The two sheets do not share their accrued interest.</strong> The
 * corporate letter's {@link #accruedInterest} comes from the bond's own schedule on
 * an actual/actual count. The G-Sec sheet's comes from that sheet's rule,
 * {@code quantum × coupon × days / 360} with the days from a 30/360 count, which is
 * why it travels as its own pair of fields. They are different numbers for the same
 * deal and neither is a rounding of the other.</p>
 *
 * <p><strong>The G-Sec charge is the opposite direction, and not the rule the
 * template computes.</strong> The committed G-Sec sheet holds {@code =C28*0.1%} —
 * 0.10% of the <em>consideration</em> — under a label reading "Tds ( 0.10%)". The
 * agreed rule is <strong>10% of the accrued-interest component</strong>, a different
 * base and a different rate, roughly three times the amount. This application's
 * figure wins and is what prints.</p>
 *
 * <p>The label on that cell still reads "0.10%" while the figure beside it is 10%
 * of the interest. That discrepancy is on the letter and is a
 * <strong>finance question, not a formatting one</strong> — either the label or
 * the rate needs to change before this letter goes to a customer.</p>
 *
 * <p>The {@code gsec*} fields are null when the deal cannot support them — no
 * maturity date, or no coupon. A government security always has both, so a G-Sec
 * letter can only be printed from a deal that supplies them, and
 * {@code GsecSellSheetStrategy} refuses rather than printing blanks.</p>
 */
public record DealConfirmationSheetValues(
        LocalDate letterDate,
        String dealReference,
        String counterpartyLine,
        String transactionType,
        String modeOfDelivery,
        LocalDate dealDate,
        LocalDate valueDate,
        String isin,
        BigDecimal price,
        String securityName,
        LocalDate maturityDate,
        String ipDateDescription,
        LocalDate lastInterestPaymentDate,
        BigDecimal couponRate,
        Long accruedDays,
        Long numberOfBonds,
        BigDecimal quantum,
        BigDecimal principalAmount,
        BigDecimal accruedInterest,
        BigDecimal stampDuty,
        BigDecimal totalConsideration,
        Long gsecAccruedDays,
        BigDecimal gsecAccruedInterest,
        BigDecimal gsecConsiderationAmount,
        BigDecimal gsecTds,
        BigDecimal gsecTotalConsiderationAmount
) {
}
