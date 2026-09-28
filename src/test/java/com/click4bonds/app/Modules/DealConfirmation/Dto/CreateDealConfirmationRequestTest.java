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
 * looked at: a missing ISIN and a non-positive number of lots. There is no
 * per-lot quantity to validate — the lot size comes from the bond, and the
 * service guards the product of it and the lot count, which bean validation
 * cannot express.</p>
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

        assertTrue(violationsOf(new CreateDealConfirmationRequest("INE123A01016", 5L))
                .isEmpty());
    }

    @Test
    void acceptsTheMinimumNumberOfLots() {

        assertTrue(violationsOf(new CreateDealConfirmationRequest("INE123A01016", 1L))
                .isEmpty());
    }

    // ============================================================
    // ISIN
    // ============================================================

    @Test
    void rejectsMissingIsin() {

        assertTrue(rejects("isin", new CreateDealConfirmationRequest(null, 5L)));
    }

    @Test
    void rejectsBlankIsin() {

        assertTrue(rejects("isin", new CreateDealConfirmationRequest("   ", 5L)));
    }

    @Test
    void rejectsIsinLongerThanTheBondApiAllows() {

        assertTrue(rejects("isin", new CreateDealConfirmationRequest("INE123A01016X", 5L)));
    }

    // ============================================================
    // NUMBER OF LOTS
    // ============================================================

    @Test
    void rejectsZeroLots() {

        assertTrue(rejects("numberOfLots", new CreateDealConfirmationRequest("INE123A01016", 0L)));
    }

    @Test
    void rejectsNegativeLots() {

        assertTrue(rejects("numberOfLots", new CreateDealConfirmationRequest("INE123A01016", -5L)));
    }

    @Test
    void rejectsMissingNumberOfLots() {

        assertTrue(rejects("numberOfLots", new CreateDealConfirmationRequest("INE123A01016", null)));
    }

    @Test
    void reportsEveryBrokenFieldAtOnce() {

        Set<ConstraintViolation<CreateDealConfirmationRequest>> violations =
                violationsOf(new CreateDealConfirmationRequest("", 0L));

        assertEquals(2, violations.size());
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
