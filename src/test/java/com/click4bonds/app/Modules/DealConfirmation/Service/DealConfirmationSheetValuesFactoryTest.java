package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

/**
 * The arithmetic behind the letter.
 *
 * <p>These are the numbers a customer reads on a confirmation of what they
 * bought, so the unit conversions get pinned individually: a coupon stored as a
 * percentage printed as a percentage, or accrued interest multiplied by a
 * quantity it was never per-unit of, are both wrong by a factor the eye does not
 * catch on a page.</p>
 */
class DealConfirmationSheetValuesFactoryTest {

    private static final LocalDate DEAL_DATE = LocalDate.of(2026, 9, 25);
    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 9, 25);

    private final DocumentProperties properties = new DocumentProperties();

    private final DealConfirmationSheetValuesFactory factory =
            new DealConfirmationSheetValuesFactory(properties);

    // =========================================================
    // MONEY
    // =========================================================

    @Test
    void multipliesTheQuantityByTheFaceValueToGetTheQuantum() {

        DealConfirmationSheetValues values = factory.build(snapshot());

        /*
         * Bond has no face-value field, so the config supplies the convention
         * (100). Quantum is the face value of the position, which is what the
         * letter shows beside "Principal Amount".
         */
        assertEquals(new BigDecimal("1100.00"), values.quantum());
    }

    @Test
    void takesThePrincipalFromWhatTheDealActuallyCharged() {

        DealConfirmationSheetValues values = factory.build(snapshot());

        /*
         * Not recomputed from quantum x price. The persisted total is what the
         * platform charged; recomputing it here would let the letter and the
         * ledger disagree.
         */
        assertEquals(new BigDecimal("1100.00"), values.principalAmount());
    }

    @Test
    void scalesAccruedInterestFromOneBondToTheWholePosition() {

        DealConfirmationSheetValues values = factory.build(snapshot());

        /*
         * AccruedInterestService returns interest for a single bond of face
         * value 100. Eleven of them accrue eleven times as much.
         */
        assertEquals(new BigDecimal("26.41"), values.accruedInterest());
    }

    @Test
    void addsPrincipalAccruedInterestAndStampDutyForTheTotal() {

        DealConfirmationSheetValues values = factory.build(snapshot());

        assertEquals(new BigDecimal("1126.41"), values.totalConsideration());
    }

    @Test
    void roundsMoneyToThePaisa() {

        /*
         * Accrued interest out of the service is high precision. XLSX stores
         * numbers as doubles and the letter is a money document, so it is
         * reduced once, here, rather than left to whatever renders it.
         */
        DealConfirmationSheetValues values = factory.build(
                snapshotWith(builder -> builder
                        .totalQuantity(3L)
                        .pricePerUnit(new BigDecimal("100.0000"))
                        .totalAmount(new BigDecimal("300.0000"))
                        .accruedInterestPerHundredFace(new BigDecimal("1.005"))));

        assertEquals(new BigDecimal("3.02"), values.accruedInterest());
        assertEquals(new BigDecimal("303.02"), values.totalConsideration());
    }

    @Test
    void chargesNoStampDutyUntilTheRuleIsConfirmed() {

        DealConfirmationSheetValues values = factory.build(snapshot());

        assertEquals(new BigDecimal("0.00"), values.stampDuty());
    }

    // =========================================================
    // THE COUPON UNIT
    // =========================================================

    @Test
    void printsTheCouponAsAFractionNotAPercentage() {

        /*
         * Bond.couponRate holds 13.70 meaning 13.70%. The cell is formatted as a
         * percentage, so it expects 0.137. Writing 13.70 into it would print
         * 1370%.
         */
        DealConfirmationSheetValues values = factory.build(snapshot());

        assertEquals(new BigDecimal("0.137000"), values.couponRateFraction());
    }

    @Test
    void leavesTheCouponBlankRatherThanZeroWhenTheBondHasNone() {

        /*
         * A null rate prints as an empty cell. Zero would read as a zero-coupon
         * bond, which is a different instrument.
         */
        DealConfirmationSheetValues values = factory.build(
                snapshotWith(builder -> builder.couponRate(null)));

        assertNull(values.couponRateFraction());
    }

    // =========================================================
    // WHAT THE LETTER SAYS
    // =========================================================

    @Test
    void prefixesTheCounterpartyLineWithTheTemplatesOwnWording() {

        DealConfirmationSheetValues values = factory.build(snapshot());

        assertEquals("Counterparty Name- Test Customer", values.counterpartyLine());
    }

    @Test
    void treatsTheCustomerAsBuyingFromUs() {

        DealConfirmationSheetValues values = factory.build(snapshot());

        assertEquals("Our Sale", values.transactionType());
    }

    @Test
    void datesTheLetterTheSameDayAsTheDeal() {

        DealConfirmationSheetValues values = factory.build(snapshot());

        assertEquals(DEAL_DATE, values.letterDate());
        assertEquals(VALUE_DATE, values.valueDate());
    }

    // =========================================================
    // REFUSING TO PRINT A LETTER WITH A HOLE IN IT
    // =========================================================

    @Test
    void refusesADealWithNoPrice() {

        /*
         * A letter showing no consideration is worse than no letter: the deal
         * stays unlettered and retryable, and the failure is logged.
         */
        DocumentGenerationException failure = assertThrows(
                DocumentGenerationException.class,
                () -> factory.build(snapshotWith(builder -> builder
                        .pricePerUnit(null)
                        .totalAmount(null))));

        assertTrue(failure.getMessage().contains("no price"));
    }

    @Test
    void refusesADealWithNoAccruedInterest() {

        /*
         * The accrual is absent when the bond's coupon schedule could not be
         * resolved, which the calculator reports as empty rather than throwing.
         * Printing the consideration without its interest component would
         * understate what the customer paid.
         */
        DocumentGenerationException failure = assertThrows(
                DocumentGenerationException.class,
                () -> factory.build(snapshotWith(builder ->
                        builder.accruedInterestPerHundredFace(null))));

        assertTrue(failure.getMessage().contains("accrued interest"));
    }

    @Test
    void refusesADealWithNoQuantity() {

        assertThrows(
                DocumentGenerationException.class,
                () -> factory.build(snapshotWith(builder -> builder.totalQuantity(null))));
    }

    @Test
    void refusesAnEmptySnapshot() {

        assertThrows(
                DocumentGenerationException.class,
                () -> factory.build(null));
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    /**
     * Eleven bonds at 100, accruing 2.401 per bond — chosen so the rounding is
     * visible in the totals rather than hidden by round numbers.
     */
    private DealConfirmationDocumentData snapshot() {

        return snapshotWith(builder -> builder);
    }

    private DealConfirmationDocumentData snapshotWith(
            java.util.function.UnaryOperator<SnapshotBuilder> customise) {

        return customise.apply(new SnapshotBuilder()).build();
    }

    /** A mutable stand-in so each test changes only the field it is about. */
    private static final class SnapshotBuilder {

        private String dealReference = "DC-20260925-000001";
        private LocalDate dealDate = DEAL_DATE;
        private LocalDate valueDate = VALUE_DATE;
        private String customerName = "Test Customer";
        private BigDecimal couponRate = new BigDecimal("13.70");
        private Long totalQuantity = 11L;
        private BigDecimal pricePerUnit = new BigDecimal("100.0000");
        private BigDecimal totalAmount = new BigDecimal("1100.0000");
        private BigDecimal accruedInterestPerHundredFace = new BigDecimal("2.401");

        SnapshotBuilder totalQuantity(Long value) {
            this.totalQuantity = value;
            return this;
        }

        SnapshotBuilder pricePerUnit(BigDecimal value) {
            this.pricePerUnit = value;
            return this;
        }

        SnapshotBuilder totalAmount(BigDecimal value) {
            this.totalAmount = value;
            return this;
        }

        SnapshotBuilder accruedInterestPerHundredFace(BigDecimal value) {
            this.accruedInterestPerHundredFace = value;
            return this;
        }

        SnapshotBuilder couponRate(BigDecimal value) {
            this.couponRate = value;
            return this;
        }

        DealConfirmationDocumentData build() {

            return new DealConfirmationDocumentData(
                    dealReference,
                    dealDate,
                    dealDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                    valueDate,
                    customerName,
                    "customer@example.com",
                    "TEST BOND 2027",
                    "INE123A07012",
                    "SECURED",
                    couponRate,
                    LocalDate.of(2027, 8, 23),
                    "23rd Of Every Month",
                    LocalDate.of(2026, 7, 23),
                    64L,
                    accruedInterestPerHundredFace,
                    100L,
                    5L,
                    totalQuantity,
                    pricePerUnit,
                    totalAmount,
                    DealConfirmationStatus.CREATED);
        }
    }
}
