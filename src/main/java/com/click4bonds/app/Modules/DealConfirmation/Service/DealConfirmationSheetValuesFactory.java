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
 *   <li>the coupon is stored as a percentage and printed as a fraction
 *       ({@code 13.70} becomes {@code 0.1370});</li>
 *   <li>a quantity becomes a quantum by multiplying by the face value;</li>
 *   <li>accrued interest is stored per bond of face value 100 and printed for
 *       the whole position.</li>
 * </ul>
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

        BigDecimal stampDuty = money(documentProperties.getDeal().getStampDuty());

        BigDecimal totalConsideration = money(
                principalAmount.add(accruedInterest).add(stampDuty));

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
                couponRateFraction(snapshot.couponRate()),
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

    /**
     * Converts the stored percentage to the fraction the letter shows.
     *
     * <p>{@code Bond.couponRate} holds {@code 13.70} meaning 13.70%; the template
     * cell is formatted as a percentage and expects {@code 0.137}. A null rate
     * prints as a blank cell rather than a zero, which would read as a
     * zero-coupon bond.</p>
     */
    private BigDecimal couponRateFraction(BigDecimal couponRate) {

        if (couponRate == null) {
            return null;
        }

        return couponRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal amount) {

        return amount == null ? null : amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String nullToEmpty(String value) {

        return value == null ? "" : value;
    }
}
