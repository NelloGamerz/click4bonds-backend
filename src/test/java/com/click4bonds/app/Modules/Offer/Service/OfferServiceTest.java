package com.click4bonds.app.Modules.Offer.Service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Dto.BondPrice;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.PriceCalculationService;
import com.click4bonds.app.Modules.Bond.Service.YtmCalculationService;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Offer.Dto.CreateOfferRequest;
import com.click4bonds.app.Modules.Offer.Dto.OfferDetails;

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    @Mock
    private BondService bondService;

    @Mock
    private PriceCalculationService priceCalculationService;

    @Mock
    private YtmCalculationService ytmCalculationService;

    @Mock
    private OfferDesignStrategyFactory designFactory;

    @Mock
    private OfferDesignStrategy design;

    @InjectMocks
    private OfferService offerService;

    private final Bond bond = Bond.builder()
            .isin("INE123A01011")
            .name("Test Bond")
            .securityType(SecurityType.SECURED)
            .couponRate(new BigDecimal("8.45"))
            .couponFrequency(CouponFrequency.HALF_YEARLY)
            .price(new BigDecimal("97.25"))
            .annualYtm(new BigDecimal("13.4000"))
            .build();

    @Test
    void derivesPriceWhenYtmIsSent() {

        when(bondService.findBond("INE123A01011")).thenReturn(bond);
        when(priceCalculationService.calculatePrice(eq(bond), any(LocalDate.class), eq(new BigDecimal("0.125"))))
                .thenReturn(new BondPrice(new BigDecimal("99.5000"), null, null, null, 0));
        when(designFactory.strategyFor("DIWALI")).thenReturn(design);
        when(design.render(any())).thenReturn(new byte[] { 1, 2, 3 });

        byte[] pdf = offerService.renderOffer(
                new CreateOfferRequest("INE123A01011", new BigDecimal("12.5"), null, "DIWALI"));

        assertArrayEquals(new byte[] { 1, 2, 3 }, pdf);

        ArgumentCaptor<OfferDetails> captor = ArgumentCaptor.forClass(OfferDetails.class);
        verify(design).render(captor.capture());

        assertEquals(new BigDecimal("99.5000"), captor.getValue().price());
        assertEquals(new BigDecimal("12.5"), captor.getValue().ytmPercentage());
        assertEquals("Secured", captor.getValue().securityType());
        assertEquals("Half yearly", captor.getValue().couponFrequency());
        verify(ytmCalculationService, never()).previewAnnualYtm(any(), any(), any());
    }

    @Test
    void derivesYtmWhenPriceIsSent() {

        when(bondService.findBond("INE123A01011")).thenReturn(bond);
        when(ytmCalculationService.previewAnnualYtm(eq(bond), any(LocalDate.class), eq(new BigDecimal("98.94"))))
                .thenReturn(new BigDecimal("12.3456"));
        when(designFactory.strategyFor(null)).thenReturn(design);
        when(design.render(any())).thenReturn(new byte[] { 4 });

        byte[] pdf = offerService.renderOffer(
                new CreateOfferRequest("INE123A01011", null, new BigDecimal("98.94"), null));

        assertArrayEquals(new byte[] { 4 }, pdf);

        ArgumentCaptor<OfferDetails> captor = ArgumentCaptor.forClass(OfferDetails.class);
        verify(design).render(captor.capture());

        assertEquals(new BigDecimal("98.94"), captor.getValue().price());
        assertEquals(new BigDecimal("12.3456"), captor.getValue().ytmPercentage());
        verify(priceCalculationService, never()).calculatePrice(any(), any(), any());
    }

    @Test
    void rejectsBothYtmAndPrice() {

        assertThrows(BadRequestException.class, () -> offerService.renderOffer(
                new CreateOfferRequest("INE123A01011",
                        new BigDecimal("12.5"), new BigDecimal("99"), null)));
    }

    @Test
    void usesTheBondsStoredPriceAndYieldWhenNeitherIsSent() {

        when(bondService.findBond("INE123A01011")).thenReturn(bond);
        when(designFactory.strategyFor(null)).thenReturn(design);
        when(design.render(any())).thenReturn(new byte[] { 3 });

        byte[] pdf = offerService.renderOffer(
                new CreateOfferRequest("INE123A01011", null, null, null));

        assertArrayEquals(new byte[] { 3 }, pdf);

        ArgumentCaptor<OfferDetails> captor = ArgumentCaptor.forClass(OfferDetails.class);
        verify(design).render(captor.capture());

        assertEquals(new BigDecimal("97.25"), captor.getValue().price());
        assertEquals(new BigDecimal("13.4000"), captor.getValue().ytmPercentage());
        verify(priceCalculationService, never()).calculatePrice(any(), any(), any());
        verify(ytmCalculationService, never()).previewAnnualYtm(any(), any(), any());
    }
}
