package com.click4bonds.app.Modules.Admin.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Admin.Dto.AdminDealPricingPreviewResponse;
import com.click4bonds.app.Modules.Admin.Dto.PreviewDealPricingRequest;
import com.click4bonds.app.Modules.Bond.Dto.BondPrice;
import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Bond.Service.PriceCalculationService;
import com.click4bonds.app.Modules.Bond.Service.YtmCalculationService;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Redis.RedisService;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealPricing;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;

/**
 * Behaviour of the pre-deal price/yield preview: the calculation is the platform
 * engine's, it is cached against the employee and bond, and nothing is written
 * to the bond.
 */
@ExtendWith(MockitoExtension.class)
class AdminDealPricingServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOND_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private RedisService redisService;

    @Mock
    private BondService bondService;

    @Mock
    private PriceCalculationService priceCalculationService;

    @Mock
    private YtmCalculationService ytmCalculationService;

    private AdminDealPricingService service;

    @BeforeEach
    void setUp() {
        service = new AdminDealPricingService(
                redisService,
                bondService,
                priceCalculationService,
                ytmCalculationService,
                new DocumentProperties());
    }

    @Test
    void solvesTheYieldFromAPriceAndCachesThePair() {

        Bond bond = bond();
        LocalDate dealDate = LocalDate.of(2026, 11, 2);

        Mockito.when(bondService.findBondById(BOND_ID)).thenReturn(bond);
        Mockito.when(ytmCalculationService.previewAnnualYtm(
                eq(bond), eq(dealDate), eq(new BigDecimal("98.94"))))
                .thenReturn(new BigDecimal("12.5000"));

        AdminDealPricingPreviewResponse preview = service.preview(
                USER_ID,
                new PreviewDealPricingRequest(
                        BOND_ID,
                        new BigDecimal("98.94"),
                        null,
                        dealDate));

        assertEquals(new BigDecimal("98.94"), preview.pricePerUnit());
        assertEquals(new BigDecimal("12.5000"), preview.annualYtm());
        assertEquals(new BigDecimal("12.1320"), preview.semiYtm());

        ArgumentCaptor<AdminDealPricingService.CachedPricing> cached =
                ArgumentCaptor.forClass(AdminDealPricingService.CachedPricing.class);

        Mockito.verify(redisService).set(
                eq("admin:deal-pricing:" + USER_ID + ":" + BOND_ID),
                cached.capture(),
                eq(Duration.ofMinutes(30)));

        assertEquals(new BigDecimal("98.94"), cached.getValue().pricePerUnit());
        assertEquals(new BigDecimal("12.5000"), cached.getValue().annualYtm());
        assertEquals(dealDate, cached.getValue().valueDate());
    }

    @Test
    void solvesThePriceFromAYieldAndCachesThePair() {

        Bond bond = bond();
        LocalDate dealDate = LocalDate.of(2026, 11, 2);

        Mockito.when(bondService.findBondById(BOND_ID)).thenReturn(bond);
        Mockito.when(priceCalculationService.calculatePrice(
                eq(bond), eq(dealDate), eq(new BigDecimal("0.125"))))
                .thenReturn(new BondPrice(
                        new BigDecimal("98.9400"),
                        new BigDecimal("100.0000000000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0));

        AdminDealPricingPreviewResponse preview = service.preview(
                USER_ID,
                new PreviewDealPricingRequest(
                        BOND_ID,
                        null,
                        new BigDecimal("12.5"),
                        dealDate));

        assertEquals(0, new BigDecimal("98.9400").compareTo(preview.pricePerUnit()));
        assertEquals(new BigDecimal("12.5"), preview.annualYtm());
    }

    @Test
    void readsTheCachedPairBackAsDealPricing() {

        Mockito.when(redisService.get(
                "admin:deal-pricing:" + USER_ID + ":" + BOND_ID,
                AdminDealPricingService.CachedPricing.class))
                .thenReturn(Optional.of(new AdminDealPricingService.CachedPricing(
                        new BigDecimal("98.94"),
                        new BigDecimal("12.5"),
                        new BigDecimal("6.25"),
                        LocalDate.of(2026, 11, 2))));

        DealPricing pricing = service.cachedPricing(USER_ID, BOND_ID).orElseThrow();

        assertEquals(new BigDecimal("98.94"), pricing.pricePerUnit());
        assertEquals(new BigDecimal("12.5"), pricing.annualYtm());
        assertEquals(new BigDecimal("6.25"), pricing.semiYtm());
    }

    @Test
    void refusesARequestThatSuppliesBothFigures() {

        assertThrows(
                BadRequestException.class,
                () -> service.preview(
                        USER_ID,
                        new PreviewDealPricingRequest(
                                BOND_ID,
                                new BigDecimal("98.94"),
                                new BigDecimal("12.5"),
                                null)));
    }

    @Test
    void refusesARequestThatSuppliesNeitherFigure() {

        assertThrows(
                BadRequestException.class,
                () -> service.preview(
                        USER_ID,
                        new PreviewDealPricingRequest(BOND_ID, null, null, null)));
    }

    private Bond bond() {

        return Bond.builder()
                .id(BOND_ID)
                .name("Test Bond")
                .isin("INE123A01016")
                .status(BondStatus.ACTIVE)
                .lotSize(new BigDecimal("100"))
                .build();
    }
}
