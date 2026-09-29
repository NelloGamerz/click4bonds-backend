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
 *       because it is a rate applied to a figure the deal itself supplies.</li>
 * </ul>
 *
 * <p><strong>The coupon rate is not converted.</strong> The letter prints it
 * exactly as {@code Bond} stores it, a percentage, so {@code 8.80} prints as
 * {@code 8.80%} and a null prints as a blank cell rather than a zero — which
 * would read as a zero-coupon bond. The template's coupon cell is formatted as
 * a percentage of a <em>fraction</em>, so it is that cell's format the letter
 * changes rather than this class that divides; see
 * {@link DealConfirmationCellMap#numberFormats()}.</p>
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

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DocumentProperties documentProperties;

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
                totalConsideration);
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
