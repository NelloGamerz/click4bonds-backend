package com.click4bonds.app.Modules.Admin.Dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The listing carries the figures of the sheet the deal is actually lettered on,
 * so a corporate row never shows a G-Sec charge and vice versa.
 */
class AdminDealSummaryResponseTest {

    private final DealConfirmation deal = DealConfirmation.builder()
            .id(UUID.randomUUID())
            .dealReference("2026/S/OCT/144")
            .isin("INE123456789")
            .build();

    @Test
    void corporateDealCarriesTheCorporateFigures() throws Exception {

        AdminDealSummaryResponse response = AdminDealSummaryResponse.from(
                deal, values(), false);

        assertEquals(new BigDecimal("120.50"), response.accruedInterest());
        assertEquals(new BigDecimal("1"), response.stampDuty());
        assertEquals(new BigDecimal("12051.50"), response.totalConsideration());
        assertNull(response.tds());

        String json = new ObjectMapper().writeValueAsString(response);
        assertTrue(json.contains("stampDuty"));
        assertFalse(json.contains("tds"));
    }

    @Test
    void gsecDealCarriesTheGsecFiguresAndNoStampDuty() throws Exception {

        AdminDealSummaryResponse response = AdminDealSummaryResponse.from(
                deal, values(), true);

        assertEquals(new BigDecimal("118.00"), response.accruedInterest());
        assertEquals(new BigDecimal("118.00"), response.totalConsideration());
        assertNull(response.stampDuty());
        assertEquals(new BigDecimal("11.80"), response.tds());

        String json = new ObjectMapper().writeValueAsString(response);
        assertTrue(json.contains("tds"));
        assertFalse(json.contains("stampDuty"));
    }

    @Test
    void aDealThatCannotBeLetteredCarriesNoFigures() {

        AdminDealSummaryResponse response = AdminDealSummaryResponse.from(
                deal, null, false);

        assertNull(response.accruedInterest());
        assertNull(response.stampDuty());
        assertNull(response.totalConsideration());
        assertNull(response.tds());
    }

    /**
     * A deal the sheet can be built from: corporate and G-Sec figures differ, so
     * a test can tell which set a row carries.
     */
    private DealConfirmationSheetValues values() {

        return new DealConfirmationSheetValues(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                500L,
                null,
                null,
                new BigDecimal("120.50"),
                null,
                new BigDecimal("1"),
                new BigDecimal("12051.50"),
                179L,
                new BigDecimal("118.00"),
                null,
                new BigDecimal("11.80"),
                new BigDecimal("118.00"),
                null, null, null, null, null, null, null);
    }
}
