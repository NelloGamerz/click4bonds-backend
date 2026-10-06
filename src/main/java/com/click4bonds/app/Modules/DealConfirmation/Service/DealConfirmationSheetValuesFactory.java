package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

import lombok.RequiredArgsConstructor;

/**
 * Turns a deal snapshot into the values a confirmation letter prints.
 *
 * <p>All of the arithmetic lives here, in one place, so the cell map can be read
 * as a layout and this as a set of sums. Every unit conversion the letter needs
 * happens exactly once:</p>
 *
 * <ul>
 *   <li>a quantity becomes a quantum by multiplying by the face value;</li>
 *   <li>accrued interest is stored per bond of face value 100 and printed for
 *       the whole position;</li>
 *   <li>stamp duty is computed from the consideration rather than configured,
 *       because it is a rate applied to a figure the deal itself supplies;</li>
 *   <li>TDS is computed from the accrued interest, likewise.</li>
 * </ul>
 *
 * <p>The two charges belong to two different letters — stamp duty on the
 * corporate sheet, TDS on the G-Sec sheet — and both are computed here so that
 * neither sheet's arithmetic lives in a layout class. Which of them a given
 * letter prints is
 * {@link DealConfirmationSheetStrategyFactory}'s business.</p>
 *
 * <p><strong>The G-Sec letter's accrued interest is a second calculation, not a
 * reuse of the first.</strong> The corporate figure is the bond's own, on an
 * actual/actual count. The G-Sec sheet's rule is its own —
 * {@code quantum x coupon x days / 360}, with the days from
 * {@code COUPDAYBS(valueDate, maturity, 2, 4)} — so it is computed here on the
 * same record and travels in its own fields. Two conventions, deliberately, for
 * two instruments.</p>
 *
 * <p><strong>The coupon rate is not converted.</strong> The letter prints it
 * exactly as {@code Bond} stores it, a percentage, so {@code 8.80} prints as
 * {@code 8.80%} and a null prints as a blank cell rather than a zero — which
 * would read as a zero-coupon bond. The template's coupon cell is formatted as
 * a percentage of a <em>fraction</em>, so it is that cell's format the letter
 * changes rather than this class that divides; see
 * {@link PsuPrivateSaleSheetStrategy#numberFormats()}.</p>
 *
 * <p><strong>Refuses to build values it cannot complete.</strong> A letter
 * missing its price or its accrued interest is a legal document with a hole in
 * it, and printing one is worse than printing none: the deal stays unlettered
 * and retryable, and the failure is logged. Validation lives here, next to the
 * fields it is about, rather than in the caller.</p>
 */
@Component
@RequiredArgsConstructor
public class DealConfirmationSheetValuesFactory {

    /** Money is printed to the paisa. */
    private static final int MONEY_SCALE = 2;

    /** Stamp duty is a whole number of rupees, as the rule is written. */
    private static final int RUPEE_SCALE = 0;

    /**
     * Stamp duty rate, as a percentage of the consideration before duty.
     *
     * <p>{@code 0.0001%} — one rupee per ten lakh. Held as a percentage rather
     * than a fraction because that is how the rule is stated, so the constant
     * reads as the rule does; the division by {@link #HUNDRED} is where the
     * percentage becomes the multiplier the arithmetic needs.</p>
     *
     * <p>The template ships this cell as a bare literal with no formula, so the
     * rate is not derivable from it. It is fixed here rather than configured
     * because it is a statutory rate, not a per-environment setting.</p>
     */
    private static final BigDecimal STAMP_DUTY_RATE_PERCENT = new BigDecimal("0.0001");

    /**
     * Tax deducted at source on a G-Sec, as a fraction of the accrued interest.
     *
     * <p>{@code 10%} — section 193 withholding on the interest component. Held as
     * a fraction rather than a percentage because it is applied directly to an
     * amount, with no conversion step to get wrong.</p>
     *
     * <p><strong>This deliberately disagrees with the G-Sec template's own
     * formula.</strong> That cell holds {@code =C28*0.1%} — 0.10% of the
     * consideration — under a label reading "Tds ( 0.10%)". The agreed rule is 10%
     * of the accrued interest, which is a different base and a different rate, and
     * roughly three times the amount. This application's figure wins, as it does
     * for accrued interest. The label the customer sees still says 0.10%; see
     * {@link DealConfirmationSheetValues} and {@code docs/deal-confirmation.md}.</p>
     */
    private static final BigDecimal TDS_RATE = new BigDecimal("0.10");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * The divisor in the G-Sec sheet's own interest formula,
     * {@code quantum x coupon x days / 360} — with {@code days} themselves from a
     * 30/360 count. See {@link GsecAccrualCalculator}.
     */
    private static final BigDecimal THREE_SIXTY = new BigDecimal("360");

    /**
     * Scale for the G-Sec division, before the result is reduced to money.
     *
     * <p>{@code 360} does not divide evenly into most positions, so the division is
     * carried out wide and rounded once at the end. Rounding twice would let the
     * paisa disagree with the sheet's own formula, which is the one figure on this
     * letter a customer can check by hand.</p>
     */
    private static final int CALCULATION_SCALE = 10;

    private final DocumentProperties documentProperties;

    private final GsecAccrualCalculator gsecAccrualCalculator;

    /**
     * @throws DocumentGenerationException when the deal lacks a figure the
     *         letter has to print
     */
    public DealConfirmationSheetValues build(DealConfirmationDocumentData snapshot) {

        requirePresent(snapshot);

        LocalDate dealDate = snapshot.dealDate();

        /*
         * The letter date is the deal date. They are separate fields because the
         * letter's date is a layout decision that has changed before, while the
         * deal date is a fact about the trade.
         */
        LocalDate letterDate = dealDate == null ? LocalDate.now() : dealDate;

        BigDecimal faceValue = BigDecimal.valueOf(
                documentProperties.getDeal().getFaceValue());

        long numberOfBonds = snapshot.totalQuantity();

        BigDecimal quantum = money(faceValue.multiply(BigDecimal.valueOf(numberOfBonds)));

        BigDecimal principalAmount = money(snapshot.totalAmount());

        /*
         * Accrued interest arrives per bond of face value 100, so the position's
         * accrued interest is that figure times the quantity — the face value
         * cancels and deliberately does not appear again here.
         */
        BigDecimal accruedInterest = money(
                snapshot.accruedInterestPerHundredFace()
                        .multiply(BigDecimal.valueOf(numberOfBonds)));

        /*
         * Stamp duty is charged on the consideration as it stands before the
         * duty is added, and rounded to the whole rupee.
         *
         * The base is deliberately not the letter's own total in C29. That cell
         * is C26+C27+C28, so taking the duty from it would make C28 a function
         * of itself. Principal plus accrued interest is the same figure the
         * total would have without any duty in it, and it keeps the template's
         * own sum true.
         */
        BigDecimal subtotal = money(principalAmount.add(accruedInterest));

        BigDecimal stampDuty = money(
                subtotal.multiply(STAMP_DUTY_RATE_PERCENT)
                        .divide(HUNDRED)
                        .setScale(RUPEE_SCALE, RoundingMode.HALF_UP));

        BigDecimal totalConsideration = money(subtotal.add(stampDuty));

        GsecMoney gsec = gsecMoney(snapshot, quantum, principalAmount);

        return new DealConfirmationSheetValues(
                letterDate,
                snapshot.dealReference(),
                "Counterparty Name- " + nullToEmpty(snapshot.customerName()),
                documentProperties.getDeal().getTransactionType(),
                documentProperties.getOrganisation().getModeOfDelivery(),
                dealDate,
                snapshot.valueDate(),
                snapshot.isin(),
                snapshot.pricePerUnit(),
                snapshot.bondName(),
                snapshot.maturityDate(),
                snapshot.ipDateDescription(),
                snapshot.previousCouponDate(),
                snapshot.couponRate(),
                snapshot.accruedDays(),
                numberOfBonds,
                quantum,
                principalAmount,
                accruedInterest,
                stampDuty,
                totalConsideration,
                gsec == null ? null : gsec.accruedDays(),
                gsec == null ? null : gsec.accruedInterest(),
                gsec == null ? null : gsec.considerationAmount(),
                gsec == null ? null : gsec.tds(),
                gsec == null ? null : gsec.totalConsiderationAmount());
    }

    /**
     * The G-Sec letter's money, on that sheet's own convention.
     *
     * <p>Separate from the corporate figures rather than a variation of them,
     * because both the days and the interest are computed differently:
     * {@code COUPDAYBS(..., 2, 4)} days on a European 30/360 count, then
     * {@code quantum x coupon x days / 360}. See {@link GsecAccrualCalculator}.</p>
     *
     * <p><strong>Returns null rather than throwing when the deal cannot support
     * it.</strong> A perpetual bond genuinely has no maturity, and a deal with no
     * coupon rate is legitimate for the corporate letter — neither is a reason to
     * refuse a letter that does not need these figures. A government security has
     * both, so {@link GsecSellSheetStrategy} is where the absence becomes a
     * refusal, next to the sheet that would have printed the blanks.</p>
     */
    private GsecMoney gsecMoney(
            DealConfirmationDocumentData snapshot,
            BigDecimal quantum,
            BigDecimal principalAmount) {

        if (snapshot.maturityDate() == null
                || snapshot.couponRate() == null
                || snapshot.valueDate() == null) {

            return null;
        }

        long accruedDays = gsecAccrualCalculator.accruedDays(
                snapshot.valueDate(),
                snapshot.maturityDate());

        /*
         * The sheet's coupon cell holds a FRACTION — 0.0734 for 7.34% — which is
         * what its own formula multiplies by. Moving the point rather than dividing
         * keeps it exact for a rate stored to two decimals.
         */
        BigDecimal couponFraction = snapshot.couponRate().movePointLeft(2);

        BigDecimal gsecAccruedInterest = money(
                quantum.multiply(couponFraction)
                        .multiply(BigDecimal.valueOf(accruedDays))
                        .divide(THREE_SIXTY, CALCULATION_SCALE, RoundingMode.HALF_UP));

        BigDecimal gsecConsideration = money(principalAmount.add(gsecAccruedInterest));

        /*
         * TDS comes off here where the corporate sheet's stamp duty goes on, and
         * it is levied on the interest rather than on the consideration.
         */
        BigDecimal gsecTds = money(gsecAccruedInterest.multiply(TDS_RATE));

        BigDecimal gsecTotal = money(gsecConsideration.subtract(gsecTds));

        return new GsecMoney(
                accruedDays,
                gsecAccruedInterest,
                gsecConsideration,
                gsecTds,
                gsecTotal);
    }

    /** The G-Sec letter's money, before it is spread across the values record. */
    private record GsecMoney(
            long accruedDays,
            BigDecimal accruedInterest,
            BigDecimal considerationAmount,
            BigDecimal tds,
            BigDecimal totalConsiderationAmount) {
    }

    /**
     * Rejects a deal the letter cannot be completed from.
     *
     * <p>Only figures the letter must print are required. A null maturity date is
     * accepted because a perpetual bond genuinely has none, and a missing
     * interest-payment description prints as an empty cell rather than a wrong
     * one.</p>
     */
    private void requirePresent(DealConfirmationDocumentData snapshot) {

        if (snapshot == null) {
            throw new DocumentGenerationException("No deal snapshot was supplied");
        }

        if (snapshot.dealReference() == null || snapshot.dealReference().isBlank()) {
            throw new DocumentGenerationException(
                    "Deal has no reference, so no letter can be produced for it");
        }

        if (snapshot.totalQuantity() == null) {
            throw new DocumentGenerationException(
                    "Deal " + snapshot.dealReference() + " has no quantity");
        }

        if (snapshot.pricePerUnit() == null || snapshot.totalAmount() == null) {
            throw new DocumentGenerationException(
                    "Deal " + snapshot.dealReference()
                            + " has no price, so a consideration cannot be printed");
        }

        if (snapshot.accruedInterestPerHundredFace() == null) {
            throw new DocumentGenerationException(
                    "Deal " + snapshot.dealReference()
                            + " has no accrued interest, so the consideration"
                            + " cannot be printed");
        }

        if (snapshot.valueDate() == null) {
            throw new DocumentGenerationException(
                    "Deal " + snapshot.dealReference() + " has no value date");
        }
    }

    private BigDecimal money(BigDecimal amount) {

        return amount == null ? null : amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String nullToEmpty(String value) {

        return value == null ? "" : value;
    }
}
