package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Service.AnalyticsService;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Service.UserService;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Bond.Dto.BondPriceUpdateRequest;
import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Dto.CachedBond;
import com.click4bonds.app.Modules.Bond.Dto.CreateBondRequest;
import com.click4bonds.app.Modules.Bond.Dto.UpdateBondRequest;
import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.RedisOperationException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Common.Redis.RedisService;
import com.click4bonds.app.Modules.User.Model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BondService {

    /**
     * Redis namespace for a single bond as served by {@link #getBond}.
     *
     * <p>The {@code v3} segment is the serialization generation: it moves when
     * what a cached {@link BondResponse} looks like changes, so entries written
     * under the old shape expire unread instead of being bound to a type they
     * no longer fit. It moved from {@code v2} when {@code BondResponse} was
     * reduced to its summary fields, which dropped the {@code id} — a stale v2
     * entry would fail to bind rather than merely carry extra keys.</p>
     */
    private static final String BOND_CACHE_PREFIX = "bond:v3:get:";

    /**
     * How long a bond read by ISIN may be served from Redis.
     *
     * <p>Every write to a bond evicts its entry, so this is a backstop rather
     * than the normal path to freshness — it bounds how long a missed eviction
     * can keep serving a stale price.</p>
     */
    private static final Duration BOND_CACHE_TTL = Duration.ofMinutes(10);

    private final BondRepository bondRepository;
    private final UserService userService;
    private final AnalyticsService analyticsService;
    private final BondRequestMapper bondRequestMapper;
    private final BondValuationService bondValuationService;
    private final RedisService redisService;

    // =========================================================
    // CREATE
    // =========================================================

    public BondResponse createBond(
            CreateBondRequest request,
            UUID adminId) {

        // -----------------------------------------------------
        // Duplicate ISIN
        // -----------------------------------------------------

        String isin = normalizeIsin(request.getIsin());

        if (bondRepository.existsByIsin(isin)) {
            throw new ConflictException(
                    "Bond with ISIN already exists: " + isin);
        }

        // -----------------------------------------------------
        // Find admin
        // -----------------------------------------------------

        User admin = userService.getUser(adminId);

        // -----------------------------------------------------
        // Parse the raw request into a Bond
        // -----------------------------------------------------
        //
        // Every loosely-typed value ("8.45%", "Secured", "7/Mar/28",
        // "1.50 Lakh") is normalized here. A value that cannot be parsed
        // rejects the whole request before anything is written.

        Bond bond = bondRequestMapper.toEntity(request, admin);

        // -----------------------------------------------------
        // Derive the other half of the price/yield pair
        // -----------------------------------------------------
        //
        // The request says which of price and annualYtm it supplied; the other is
        // calculated here, so the stored bond holds two readings of the same bond
        // rather than one number and a blank. A request that declared neither is
        // left untouched, as it always was.

        bondValuationService.applyCalculationType(bond, LocalDate.now());

        evictCachedBond(isin);

        Bond savedBond = bondRepository.save(bond);

        return mapToResponse(savedBond);
    }

    public List<BondResponse> updatePrices(
            List<BondPriceUpdateRequest> requests) {

        List<Bond> bonds = new java.util.ArrayList<>();

        for (BondPriceUpdateRequest request : requests) {
            String isin = request.getIsin().trim().toUpperCase();
            Bond bond = bondRepository.findByIsin(isin)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Bond not found with ISIN: " + isin));

            if (bond.getStatus() == BondStatus.MATURED) {
                throw new BadRequestException(
                        "Matured bond cannot be updated: " + isin);
            }

            bond.setPrice(request.getPrice());
            invalidateYieldCalculation(bond);
            evictCachedBond(isin);
            bonds.add(bond);
        }

        return bondRepository.saveAll(bonds).stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =========================================================
    // GET SINGLE BOND
    // =========================================================

    @Transactional(readOnly = true)
    @Observed(
            name = "bond.get",
            contextualName = "bond-get-by-isin"
    )
//    public BondResponse getBond(String isin, UUID userId) {
//
//        Bond bond = getbondByIs(isin);
//
//        if (userId != null && userService.hasRole(userId, UserRole.CUSTOMER)) {
//
//            analyticsService.track(
//                    AnalyticsEventType.BOND_VIEW,
//                    userId,
//                    null,
//                    bond.getId(),
//                    "WEB",
//                    "BOND_DETAILS",
//                    Map.of(
//                            "source", "bond-details",
//                            "action", "view"
//                    )
//            );
//        }
//
//        return mapToResponse(bond);
//    }

        public BondResponse getBond(
                String isin,
                UUID userId,
                boolean isCustomer) {

            log.info("Fetching bond: isin={}, userId={}", isin, userId);

            CachedBond cached = getCachedBond(isin);

            BondResponse bond = cached.getBond();

            log.info(
                    "Bond fetched successfully: isin={}, bondId={}",
                    isin,
                    cached.getId()
            );

            if (userId != null) {

                log.info(
                        "Analytics role check: userId={}, role={}, isCustomer={}",
                        userId,
                        UserRole.CUSTOMER,
                        isCustomer
                );

                if (isCustomer) {

                    log.info(
                            "Tracking BOND_VIEW analytics event: userId={}, bondId={}, source=WEB, page=BOND_DETAILS",
                            userId,
                            cached.getId()
                    );

                    analyticsService.track(
                            AnalyticsEventType.BOND_VIEW,
                            userId,
                            null,
                            cached.getId(),
                            "WEB",
                            "BOND_DETAILS",
                            Map.of(
                                    "source", "bond-details",
                                    "action", "view"
                            )
                    );

                    log.info(
                            "BOND_VIEW analytics event submitted: userId={}, bondId={}",
                            userId,
                            cached.getId()
                    );
                } else {

                    log.info(
                            "Skipping BOND_VIEW analytics: user is not a CUSTOMER. userId={}",
                            userId
                    );
                }

            } else {

                log.info(
                        "Skipping BOND_VIEW analytics: userId is null, isin={}",
                        isin
                );
            }

            return bond;
        }

    // =========================================================
    // CACHE
    // =========================================================

    /**
     * Reads a bond through Redis, loading it from the database only on a miss.
     *
     * <p>The cache is an optimisation, not a second source of truth: a Redis
     * failure falls back to a database read rather than failing the request, so
     * an unreachable cache cannot take bond detail down with it. Writes are the
     * other half of this — see {@link #evictCachedBond}.</p>
     *
     * <p>The entry is a {@link CachedBond} rather than a bare
     * {@link BondResponse} because the caller needs the bond's id for analytics
     * while the response no longer carries one; see {@link CachedBond}.</p>
     */
    private CachedBond getCachedBond(String isin) {

        String normalizedIsin = normalizeIsin(isin);

        if (normalizedIsin == null) {
            return toCachedBond(getbondByIs(isin));
        }

        String cacheKey = BOND_CACHE_PREFIX + normalizedIsin;

        try {
            Optional<CachedBond> cached =
                    redisService.get(cacheKey, CachedBond.class);

            if (cached.isPresent()) {
                return cached.get();
            }
        } catch (RedisOperationException ex) {
            log.warn("Bond cache read failed, reading from the database instead: isin={}",
                    normalizedIsin, ex);
        }

        CachedBond bond = toCachedBond(getbondByIs(normalizedIsin));

        try {
            redisService.set(cacheKey, bond, BOND_CACHE_TTL);
        } catch (RedisOperationException ex) {
            log.warn("Bond cache write failed: isin={}", normalizedIsin, ex);
        }

        return bond;
    }

    private CachedBond toCachedBond(Bond bond) {

        return new CachedBond(
                bond.getId(),
                mapToResponse(bond));
    }

    /**
     * Drops the cached read for {@code isin}.
     *
     * <p>Called before every write, so the failure modes stay consistent: if
     * Redis is unreachable this throws and the surrounding transaction rolls
     * back, leaving the database and the cache in agreement. Evicting after the
     * write instead would let a failed eviction pair a committed new price with
     * a stale cached one.</p>
     */
    private void evictCachedBond(String isin) {

        String normalizedIsin = normalizeIsin(isin);

        if (normalizedIsin == null) {
            return;
        }

        redisService.delete(BOND_CACHE_PREFIX + normalizedIsin);
    }

    // =========================================================
    // GET ALL BONDS
    // =========================================================

    @Transactional(readOnly = true)
    @Observed(
            name = "bond.list",
            contextualName = "bond-list-all"
    )
    public Page<BondResponse> getBonds(Pageable pageable) {

        return bondRepository
                .findAll(pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    @Observed(
            name = "bond.list.search",
            contextualName = "bond-list-search"
    )
    public Page<BondResponse> getBonds(
            String search,
            Boolean isFlashNews,
            Pageable pageable) {
        return getBonds(search, isFlashNews, pageable, null, false);
    }

    public Page<BondResponse> getBonds(
            String search,
            Boolean isFlashNews,
            Pageable pageable,
            UUID userId,
            boolean isCustomer) {
        if ((search == null || search.isBlank()) && isFlashNews == null) {
            return getBonds(pageable);
        }

        Page<BondResponse> bonds = bondRepository
                .searchBonds(search, isFlashNews, pageable)
                .map(this::mapToResponse);

        if (userId != null && isCustomer) {
            if (search != null && !search.isBlank()) {
                analyticsService.track(
                        AnalyticsEventType.BOND_SEARCH,
                        userId,
                        null,
                        null,
                        "WEB",
                        "BOND_LIST",
                        Map.of(
                                "searchLength", search.trim().length(),
                                "resultCount", bonds.getTotalElements()));
            }

            if (isFlashNews != null) {
                analyticsService.track(
                        AnalyticsEventType.BOND_FILTER,
                        userId,
                        null,
                        null,
                        "WEB",
                        "BOND_LIST",
                        Map.of("filter", "flashNews", "value", isFlashNews));
            }

            if (bonds.isEmpty()) {
                analyticsService.track(
                        AnalyticsEventType.SEARCH_NO_RESULT,
                        userId,
                        null,
                        null,
                        "WEB",
                        "BOND_LIST",
                        Map.of(
                                "searchApplied", search != null && !search.isBlank(),
                                "filterApplied", isFlashNews != null));
            }
        }

        return bonds;
    }

    // =========================================================
    // GET ACTIVE BONDS
    // =========================================================

    @Transactional(readOnly = true)
    public Page<BondResponse> getActiveBonds(Pageable pageable) {

        return bondRepository
                .findByStatus(
                        BondStatus.ACTIVE,
                        pageable)
                .map(this::mapToResponse);
    }

    // =========================================================
    // UPDATE
    // =========================================================

    public BondResponse updateBond(
            String isin,
            UpdateBondRequest request) {

        Bond bond = getbondByIs(isin);

        // -----------------------------------------------------
        // Matured bond cannot be modified
        // -----------------------------------------------------

        if (bond.getStatus() == BondStatus.MATURED) {

            throw new BadRequestException(
                    "Matured bond cannot be updated");
        }

        // -----------------------------------------------------
        // Parse and apply the raw request
        // -----------------------------------------------------
        //
        // Null fields are left untouched; non-null fields are parsed the same
        // way as on create, so an update can carry raw sheet values too.

        bondRequestMapper.applyUpdate(bond, request);

        // -----------------------------------------------------
        // Derive the other half of the price/yield pair
        // -----------------------------------------------------
        //
        // The same derivation create performs, and on the same terms: the bond's
        // calculationType says which of price and annualYtm is the input, and the
        // other is worked out from it.
        //
        // Only run when this request actually moved the price or the yield. A
        // patch that touches neither has nothing new to derive from, and deriving
        // anyway would re-price the bond off figures the caller never sent —
        // turning a rename into a revaluation. A bond whose calculationType was
        // never declared is left untouched either way.

        if (request.getPrice() != null || request.getAnnualYtm() != null) {
            bondValuationService.applyCalculationType(bond, LocalDate.now());
        }

        // -----------------------------------------------------
        // Save
        // -----------------------------------------------------

        evictCachedBond(isin);

        Bond savedBond = bondRepository.save(bond);

        return mapToResponse(savedBond);
    }

    // =========================================================
    // ACTIVATE
    // =========================================================

    public BondResponse activateBond(String isin) {

        Bond bond = getbondByIs(isin);

        // -----------------------------------------------------
        // Validate current state
        // -----------------------------------------------------

        if (bond.getStatus() == BondStatus.MATURED) {
            throw new BadRequestException(
                    "Matured bond cannot be activated");
        }

        if (bond.getStatus() == BondStatus.CANCELLED) {
            throw new BadRequestException(
                    "Cancelled bond cannot be activated");
        }

        if (bond.getMaturityType() == null) {
            throw new BadRequestException(
                    "Bond maturity type is required");
        }

        /*
         * For fixed maturity bonds, maturity date is mandatory.
         */
        if (bond.getMaturityType().name().equals("FIXED")
                && bond.getMaturityDate() == null) {

            throw new BadRequestException(
                    "Bond maturity date is required");
        }

        bond.setStatus(BondStatus.ACTIVE);

        evictCachedBond(isin);

        return mapToResponse(
                bondRepository.save(bond));
    }

    // =========================================================
    // SUSPEND
    // =========================================================

    public BondResponse suspendBond(String isin) {

        Bond bond = getbondByIs(isin);

        if (bond.getStatus() == BondStatus.MATURED) {
            throw new BadRequestException(
                    "Matured bond cannot be suspended");
        }

        if (bond.getStatus() == BondStatus.CANCELLED) {
            throw new BadRequestException(
                    "Cancelled bond cannot be suspended");
        }

        bond.setStatus(BondStatus.SUSPENDED);

        evictCachedBond(isin);

        return mapToResponse(
                bondRepository.save(bond));
    }

    // =========================================================
    // CANCEL
    // =========================================================

    public void cancelBond(String isin) {

        Bond bond = getbondByIs(isin);

        if (bond.getStatus() == BondStatus.MATURED) {
            throw new BadRequestException(
                    "Matured bond cannot be cancelled");
        }

        if (bond.getStatus() == BondStatus.CANCELLED) {
            throw new BadRequestException(
                    "Bond is already cancelled");
        }

        /*
         * Purchase/order validation should be added here
         * once your investment/order model exists.
         *
         * Example:
         *
         * if (orderRepository.existsByBondIdAndActive(...)) {
         * throw new BadRequestException(
         * "Bond with existing purchases cannot be cancelled");
         * }
         */

        bond.setStatus(BondStatus.CANCELLED);

        evictCachedBond(isin);

        bondRepository.save(bond);
    }

    // =========================================================
    // INTERNAL ENTITY LOOKUP
    // =========================================================

    public Bond findBond(String isin) {
        return getbondByIs(isin);
    }

    /**
     * Looks a bond up by its ISIN.
     * <p>
     * ISINs are stored upper-cased, so the input is normalized
     * first — that way an ISIN typed in lower case in a path
     * variable still resolves.
     */
    private Bond getbondByIs(String isin) {

        String normalizedIsin = normalizeIsin(isin);

        return bondRepository.findByIsin(normalizedIsin)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with ISIN: " + normalizedIsin));
    }

    private String normalizeIsin(String isin) {

        return isin == null
                ? null
                : isin.trim().toUpperCase();
    }

    // =========================================================
    // INVALIDATE YIELD
    // =========================================================

    private void invalidateYieldCalculation(Bond bond) {

        bond.setSemiYtm(null);
        bond.setAnnualYtm(null);
        bond.setYtc(null);
        bond.setYtmCalculatedAt(null);
    }

    // =========================================================
    // ENTITY -> RESPONSE
    // =========================================================

    private BondResponse mapToResponse(Bond bond) {

        return BondResponse.builder()

                .name(
                        bond.getName())

                .isin(
                        bond.getIsin())

                .couponRate(
                        bond.getCouponRate())

                .maturityDate(
                        bond.getMaturityDate())

                .rating(
                        combineRating(
                                bond.getRatingAgency(),
                                bond.getRating()))

                .minimumInvestment(
                        minimumInvestment(
                                bond.getPrice(),
                                bond.getLotSize()))

                .build();
    }

    /**
     * Joins the rating agency and the grade into the single string the response
     * carries, e.g. {@code "CRISIL AAA"}.
     *
     * <p>A bond may have been entered with only one of the two — or neither —
     * so what is present is what is shown, and a bond with no rating at all
     * reports null rather than an empty string.</p>
     */
    private static String combineRating(
            String ratingAgency,
            String rating) {

        boolean hasAgency = ratingAgency != null && !ratingAgency.isBlank();
        boolean hasRating = rating != null && !rating.isBlank();

        if (hasAgency && hasRating) {
            return ratingAgency.trim() + " " + rating.trim();
        }

        if (hasAgency) {
            return ratingAgency.trim();
        }

        return hasRating ? rating.trim() : null;
    }

    /**
     * What one lot costs: the price is quoted per 100 of face value, so
     * {@code lotSize} units come to {@code price × lotSize ÷ 100}.
     *
     * <p>Null when either input is unknown. A bond with no price has nothing to
     * multiply, and a bond trading in DEMAT/SGL has no numeric lot size at all
     * — in both cases "unknown" is the honest answer, where a zero would read
     * as a bond that costs nothing.</p>
     */
    private static BigDecimal minimumInvestment(
            BigDecimal price,
            BigDecimal lotSize) {

        if (price == null || lotSize == null) {
            return null;
        }

        // Rupees and paise; dividing by 100 is exact, but the scale is stated
        // so the response does not inherit whatever precision the two inputs
        // happened to carry.
        return price
                .multiply(lotSize)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}