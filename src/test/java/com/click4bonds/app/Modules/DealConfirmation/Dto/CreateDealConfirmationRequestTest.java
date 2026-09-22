package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Bean validation on the create-deal request.
 *
 * <p>Covers the cases the API contract promises to reject before any bond is
 * looked at: a missing ISIN, a non-positive lot quantity, a non-positive number
 * of lots. The service additionally guards the product of the two, which bean
 * validation cannot express.</p>
 */
class CreateDealConfirmationRequestTest {

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        /*
         * The factory stays open for the class: closing it while the validator is
         * still in use would be a false test failure waiting to happen.
         */
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    // ============================================================
    // VALID
    // ============================================================

    @Test
    void acceptsAWellFormedRequest() {

        assertTrue(violationsOf(new CreateDealConfirmationRequest("INE123A01016", 100L, 5L))
                .isEmpty());
    }

    @Test
    void acceptsTheMinimumPositiveQuantities() {

        assertTrue(violationsOf(new CreateDealConfirmationRequest("INE123A01016", 1L, 1L))
                .isEmpty());
    }

    // ============================================================
    // ISIN
    // ============================================================

    @Test
    void rejectsMissingIsin() {

        assertTrue(rejects("isin", new CreateDealConfirmationRequest(null, 100L, 5L)));
    }

    @Test
    void rejectsBlankIsin() {

        assertTrue(rejects("isin", new CreateDealConfirmationRequest("   ", 100L, 5L)));
    }

    @Test
    void rejectsIsinLongerThanTheBondApiAllows() {

        assertTrue(rejects("isin", new CreateDealConfirmationRequest("INE123A01016X", 100L, 5L)));
    }

    // ============================================================
    // QUANTITIES
    // ============================================================

    @Test
    void rejectsZeroQuantityPerLot() {

        assertTrue(rejects("quantityPerLot", new CreateDealConfirmationRequest("INE123A01016", 0L, 5L)));
    }

    @Test
    void rejectsNegativeQuantityPerLot() {

        assertTrue(rejects("quantityPerLot", new CreateDealConfirmationRequest("INE123A01016", -1L, 5L)));
    }

    @Test
    void rejectsMissingQuantityPerLot() {

        assertTrue(rejects("quantityPerLot", new CreateDealConfirmationRequest("INE123A01016", null, 5L)));
    }

    @Test
    void rejectsZeroLots() {

        assertTrue(rejects("numberOfLots", new CreateDealConfirmationRequest("INE123A01016", 100L, 0L)));
    }

    @Test
    void rejectsNegativeLots() {

        assertTrue(rejects("numberOfLots", new CreateDealConfirmationRequest("INE123A01016", 100L, -5L)));
    }

    @Test
    void rejectsMissingNumberOfLots() {

        assertTrue(rejects("numberOfLots", new CreateDealConfirmationRequest("INE123A01016", 100L, null)));
    }

    @Test
    void reportsEveryBrokenFieldAtOnce() {

        Set<ConstraintViolation<CreateDealConfirmationRequest>> violations =
                violationsOf(new CreateDealConfirmationRequest("", 0L, 0L));

        assertEquals(3, violations.size());
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Set<ConstraintViolation<CreateDealConfirmationRequest>> violationsOf(
            CreateDealConfirmationRequest request) {

        return validator.validate(request);
    }

    private boolean rejects(
            String field,
            CreateDealConfirmationRequest request) {

        return violationsOf(request).stream()
                .anyMatch(violation -> violation.getPropertyPath()
                        .toString()
                        .equals(field));
    }
}
