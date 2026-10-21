package com.click4bonds.app.Modules.Bond.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.click4bonds.app.Config.RedisConfig;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;
import com.click4bonds.app.Modules.Bond.Dto.AdminBondDetailResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondDetailResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Redis.RedisService;
import com.click4bonds.app.Modules.Storage.Config.StorageProperties;
import com.click4bonds.app.Modules.User.Service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Covers the split between the public bond detail and the admin's.
 *
 * <p>The two responses differ by the import provenance columns, and the risk in
 * building one from the other is that the shared half silently stops being
 * copied — this test asserts the shared fields are present on both, not just
 * the three that make the admin view different.</p>
 */
class AdminBondDetailTest {

    private static final String ISIN = "IN1620170143";
    private static final UUID BOND_ID =
            UUID.fromString("6f6e4c1a-3e0c-4a5e-9c1e-3f3a1c2b4d5e");

    private BondRepository bondRepository;
    private RedisService redisService;
    private BondService bondService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {

        bondRepository = mock(BondRepository.class);
        redisService = mock(RedisService.class);

        when(bondRepository.findByIsin(ISIN)).thenReturn(Optional.of(bond()));
        when(redisService.get(anyString(), any())).thenReturn(Optional.empty());

        mapper = new RedisConfig().redisObjectMapper();

        bondService = new BondService(
                bondRepository,
                mock(UserService.class),
                mock(AnalyticsService.class),
                mock(BondRequestMapper.class),
                mock(BondValuationService.class),
                redisService,
                new StorageProperties());
    }

    // =========================================================
    // THE PROVENANCE THE ADMIN VIEW ADDS
    // =========================================================

    @Test
    void shouldCarryTheImportProvenanceInTheAdminView() {

        AdminBondDetailResponse bond = bondService.getBondForAdmin(ISIN);

        assertEquals("bonds-oct.xlsx", bond.getSourceFileName());
        assertEquals(42, bond.getSourceRowNumber());
        assertEquals(Instant.parse("2026-08-02T10:11:12Z"), bond.getImportedAt());
    }

    @Test
    void shouldCarryEverySharedFieldInTheAdminView() {

        AdminBondDetailResponse bond = bondService.getBondForAdmin(ISIN);

        assertSharedFields(bond);
    }

    @Test
    void shouldCarryEverySharedFieldInThePublicView() {

        BondDetailResponse bond = bondService.getBond(ISIN, null, false);

        assertSharedFields(bond);
    }

    // =========================================================
    // THE ADMIN LIST CARRIES FULL ROWS
    // =========================================================

    @Test
    void shouldReturnFullRowsFromTheAdminList() {

        when(bondRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bond())));

        Page<AdminBondDetailResponse> page =
                bondService.getBondsForAdmin(null, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());

        AdminBondDetailResponse row = page.getContent().get(0);

        assertSharedFields(row);
        assertEquals("bonds-oct.xlsx", row.getSourceFileName());
        assertEquals(42, row.getSourceRowNumber());
        assertEquals(Instant.parse("2026-08-02T10:11:12Z"), row.getImportedAt());
    }

    @Test
    void shouldSerializeTheFullRowInTheAdminList() {

        when(bondRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bond())));

        String json = write(
                bondService.getBondsForAdmin(null, PageRequest.of(0, 20)));

        // The summary's omissions are the whole point: a list row that carried
        // none of these is exactly what the admin was asking to stop getting.
        assertTrue(json.contains("\"price\""), json);
        assertTrue(json.contains("\"annualYtm\""), json);
        assertTrue(json.contains("\"couponRate\""), json);
        assertTrue(json.contains("\"sourceFileName\""), json);
    }

    @Test
    void shouldSearchTheAdminListThroughTheSearchQuery() {

        when(bondRepository.searchBonds(
                eq("SDL"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(bond())));

        Page<AdminBondDetailResponse> page =
                bondService.getBondsForAdmin("SDL", PageRequest.of(0, 20));

        assertEquals(ISIN, page.getContent().get(0).getIsin());
    }

    @Test
    void shouldNotTrackAnAnalyticsViewForTheAdminList() {

        when(bondRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bond())));

        AnalyticsService analytics = mock(AnalyticsService.class);

        BondService service = new BondService(
                bondRepository,
                mock(UserService.class),
                analytics,
                mock(BondRequestMapper.class),
                mock(BondValuationService.class),
                redisService,
                new StorageProperties());

        service.getBondsForAdmin(null, PageRequest.of(0, 20));

        verify(analytics, never()).track(
                any(), any(), any(), any(), anyString(), anyString(), any());
    }

    // =========================================================
    // THE PUBLIC LIST IS UNCHANGED
    // =========================================================

    @Test
    void shouldKeepThePublicListOnTheSummary() {

        when(bondRepository.findByStatus(eq(BondStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bond())));

        Page<BondResponse> page = bondService.getActiveBonds(
                null, null, null, null, null, PageRequest.of(0, 20), null, false);

        assertEquals(1, page.getTotalElements());

        String json = write(page);

        assertTrue(json.contains("\"name\""), json);
        assertFalse(json.contains("\"price\""),
                "The public list must stay on the summary");
        assertFalse(json.contains("\"sourceFileName\""), json);
    }

    // =========================================================
    // THE PUBLIC VIEW IS NOT WIDENED
    // =========================================================

    @Test
    void shouldNotPublishTheProvenanceToThePublicView() {

        BondDetailResponse bond = bondService.getBond(ISIN, null, false);

        String json = write(bond);

        assertFalse(json.contains("sourceFileName"),
                "A customer must not see which import produced the row");
        assertFalse(json.contains("sourceRowNumber"));
        assertFalse(json.contains("importedAt"));
    }

    @Test
    void shouldSerializeTheProvenanceInTheAdminView() {

        String json = write(bondService.getBondForAdmin(ISIN));

        assertTrue(json.contains("\"sourceFileName\":\"bonds-oct.xlsx\""), json);
        assertTrue(json.contains("\"sourceRowNumber\":42"), json);
        assertTrue(json.contains("\"importedAt\""), json);

        // The inherited half must survive serialization too, not just the
        // getters — a response is only as complete as its JSON.
        assertTrue(json.contains("\"isin\":\"" + ISIN + "\""), json);
    }

    // =========================================================
    // THE ADMIN READ BYPASSES THE PUBLIC CACHE
    // =========================================================

    @Test
    void shouldReadTheAdminViewFromTheDatabaseNotTheCache() {

        bondService.getBondForAdmin(ISIN);

        verify(redisService, never()).get(anyString(), any());
        verify(bondRepository, times(1)).findByIsin(ISIN);
    }

    @Test
    void shouldNotCacheTheAdminViewUnderThePublicKey() {

        bondService.getBondForAdmin(ISIN);

        verify(redisService, never()).set(anyString(), any(), any());
    }

    @Test
    void shouldNotTrackAnAnalyticsViewForTheAdminRead() {

        AnalyticsService analytics = mock(AnalyticsService.class);

        BondService service = new BondService(
                bondRepository,
                mock(UserService.class),
                analytics,
                mock(BondRequestMapper.class),
                mock(BondValuationService.class),
                redisService,
                new StorageProperties());

        assertNotNull(service.getBondForAdmin(ISIN));

        verify(analytics, never()).track(
                any(), any(), any(), any(), anyString(), anyString(), any());
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    /**
     * Asserts the fields both detail views are built from.
     *
     * <p>Takes the base type so the same assertions run against either — which
     * is the point: an admin response that lost a shared field would fail here
     * just as the public one would.</p>
     */
    private static void assertSharedFields(BondDetailResponse bond) {

        assertEquals(BOND_ID, bond.getId());
        assertEquals(1, bond.getSerialNumber());
        assertEquals("8.45% HAR SDL 2028", bond.getName());
        assertEquals(ISIN, bond.getIsin());
        assertEquals("CRISIL", bond.getRatingAgency());
        assertEquals("AAA", bond.getRating());
        assertEquals(CouponFrequency.HALF_YEARLY, bond.getCouponFrequency());
        assertEquals(MaturityType.FIXED, bond.getMaturityType());
        assertEquals(LocalDate.of(2028, 2, 9), bond.getMaturityDate());
        assertEquals(BondStatus.ACTIVE, bond.getStatus());
        assertEquals(0, bond.getCouponRate().compareTo(new BigDecimal("8.45")));
        assertEquals(0, bond.getAnnualYtm().compareTo(new BigDecimal("8.6521")));
        assertEquals(0, bond.getPrice().compareTo(new BigDecimal("102.08")));
        assertEquals(0, bond.getLotSize().compareTo(new BigDecimal("1000")));
        assertEquals(300, bond.getMaxNoOfBonds());
    }

    private String write(Object value) {

        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize the response", ex);
        }
    }

    private static Bond bond() {

        return Bond.builder()
                .id(BOND_ID)
                .serialNumber(1)
                .name("8.45% HAR SDL 2028")
                .isin(ISIN)
                .couponRate(new BigDecimal("8.45"))
                .couponFrequency(CouponFrequency.HALF_YEARLY)
                .annualYtm(new BigDecimal("8.6521"))
                .maturityType(MaturityType.FIXED)
                .maturityDate(LocalDate.of(2028, 2, 9))
                .ratingAgency("CRISIL")
                .rating("AAA")
                .price(new BigDecimal("102.08"))
                .quantum(new BigDecimal("300000"))
                .lotSize(new BigDecimal("1000"))
                .status(BondStatus.ACTIVE)
                .sourceFileName("bonds-oct.xlsx")
                .sourceRowNumber(42)
                .importedAt(Instant.parse("2026-08-02T10:11:12Z"))
                .build();
    }
}
