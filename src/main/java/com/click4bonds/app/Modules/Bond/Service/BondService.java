package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

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

import com.click4bonds.app.Modules.Bond.Dto.AdminBondDetailResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondDetailResponse;
import com.click4bonds.app.Modules.Bond.Dto.BondPriceUpdateRequest;
import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Dto.CachedBond;
import com.click4bonds.app.Modules.Bond.Dto.CreateBondRequest;
import com.click4bonds.app.Modules.Bond.Dto.UpdateBondRequest;
import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.RedisOperationException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.Common.Redis.RedisService;
import com.click4bonds.app.Modules.Storage.Config.StorageProperties;
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
     * <p>The {@code v4} segment is the serialization generation: it moves when
     * what a cached {@link CachedBond} looks like changes, so entries written
     * under the old shape expire unread instead of being bound to a type they no
     * longer fit. It moved from {@code v3} when the cached value became the full
     * {@link BondDetailResponse} rather than the summary, and from {@code v2}
     * before that when the response was reduced to summary fields — either way a
     * stale entry would fail to bind rather than merely carry extra keys.</p>
     */
    private static final String BOND_CACHE_PREFIX = "bond:v5:get:";

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
    private final StorageProperties storageProperties;

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

        public BondDetailResponse getBond(
                String isin,
                UUID userId,
                boolean isCustomer) {

            log.info("Fetching bond: isin={}, userId={}", isin, userId);

            CachedBond cached = getCachedBond(isin);

            BondDetailResponse bond = cached.getBond();

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

    /**
     * One bond in full, with its import provenance, for an admin.
     *
     * <p>Read straight from the database rather than through {@link
     * #getCachedBond}. That is not only a preference: the cache holds a
     * {@link BondDetailResponse}, which by construction cannot carry the
     * provenance fields an admin asked for. An edit screen also wants the
     * committed row rather than a copy that may be minutes old, and the price
     * this returns is the one the next PATCH will be applied to.</p>
     *
     * <p>No analytics, for the same reason the admin endpoint tracks none: a
     * BOND_VIEW means a customer looked at a bond, and an admin opening a bond
     * to edit it is a different event.</p>
     */
    @Transactional(readOnly = true)
    @Observed(
            name = "bond.get.admin",
            contextualName = "bond-get-admin-by-isin"
    )
    public AdminBondDetailResponse getBondForAdmin(String isin) {

        Bond bond = getbondByIs(isin);

        log.info(
                "Fetching bond for admin: isin={}, bondId={}",
                bond.getIsin(),
                bond.getId()
        );

        return mapToAdminDetail(bond);
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
     * <p>What is cached is the detail view, since this is the read that serves a
     * bond's own page, wrapped in a {@link CachedBond} so the bond's id travels
     * with it — analytics needs the id and the response no longer carries one.
     * The list path maps straight from the repository and does not come through
     * here; a summary is cheap to build and is never read one bond at a time.</p>
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
                mapToDetail(bond));
    }

    /**
     * Drops the cached read for {@code isin}.
     *
     * <p>Called before every write, so the failure modes stay consistent: if
     * Redis is unreachable this throws and the surrounding transaction rolls
     * back, leaving the database and the cache in agreement. Evicting after the
     * write instead would let a failed eviction pair a committed new price with
     * a stale cached one.</p>
     *
     * <p>Public because a write does not have to originate here to invalidate
     * the cache: {@link BondYtmService} calculates and stores a yield on a read
     * path, and it needs the same guarantee even though the write it precedes is
     * someone else's.</p>
     */
    public void evictCachedBond(String isin) {

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
        return getBonds(
                search,
                isFlashNews,
                null,
                null,
                null,
                pageable,
                null,
                false);
    }

    public Page<BondResponse> getBonds(
            String search,
            Boolean isFlashNews,
            SecurityType securityType,
            CouponFrequency couponFrequency,
            String rating,
            Pageable pageable,
            UUID userId,
            boolean isCustomer) {

        return listBonds(
                search,
                isFlashNews,
                securityType,
                couponFrequency,
                rating,
                pageable,
                userId,
                isCustomer,
                null,
                this::mapToResponse);
    }

    /**
     * The admin's listing: the same rows as {@link #getBonds}, each in full
     * rather than as a {@link BondResponse} summary.
     *
     * <p>An admin list is not a catalogue. Every bond in it is one the caller
     * may have to edit, re-price or trace back to an import, and the summary
     * was picked for a customer's card — nine fields, no price or yield. So the
     * rows here carry everything {@link AdminBondDetailResponse} does,
     * provenance included, matching what {@link #getBondForAdmin} returns for a
     * single bond. The public list is untouched and still gets the summary.</p>
     *
     * <p>Like the admin's single-bond read this tracks no analytics: an admin
     * browsing to manage is not a customer browsing to buy. Nor does it narrow
     * by the catalogue's facets — an admin manages every bond, so it passes the
     * filter parameters of {@link #listBonds} as null and sees what it always
     * saw.</p>
     */
    @Transactional(readOnly = true)
    @Observed(
            name = "bond.list.admin",
            contextualName = "bond-list-admin"
    )
    public Page<AdminBondDetailResponse> getBondsForAdmin(
            String search,
            Pageable pageable) {

        return listBonds(
                search,
                null,
                null,
                null,
                null,
                pageable,
                null,
                false,
                null,
                this::mapToAdminDetail);
    }

    /**
     * The same listing restricted to {@link BondStatus#ACTIVE}.
     *
     * <p>This is what the public endpoint serves. Because the status predicate
     * lives in the query, the {@code Page} carries the right numbers as well as
     * the right rows: {@code totalElements} counts the active bonds, not every
     * bond in the table, and paging walks the active set rather than skipping
     * over inactive bonds on its way through. The catalogue's facet filters
     * live there for the same reason.</p>
     */
    @Transactional(readOnly = true)
    @Observed(
            name = "bond.list.active",
            contextualName = "bond-list-active"
    )
    public Page<BondResponse> getActiveBonds(
            String search,
            Boolean isFlashNews,
            SecurityType securityType,
            CouponFrequency couponFrequency,
            String rating,
            Pageable pageable,
            UUID userId,
            boolean isCustomer) {

        return listBonds(
                search,
                isFlashNews,
                securityType,
                couponFrequency,
                rating,
                pageable,
                userId,
                isCustomer,
                BondStatus.ACTIVE,
                this::mapToResponse);
    }

    /**
     * The listing every caller is built from.
     *
     * <p>Two things separate the callers, and both are parameters here. {@code
     * status} decides which bonds are listed — null for every bond, a status
     * for only that one — and {@code mapper} decides what each row looks like,
     * the summary or the full detail. Keeping them parameters rather than
     * writing near-identical methods means the query choice and the analytics
     * below cannot drift apart between the public and admin paths.</p>
     *
     * <p>{@code securityType}, {@code couponFrequency} and {@code rating} are
     * the catalogue's facets. All three are optional; a null one is not a
     * filter, and the admin path passes null for all three so it lists what it
     * always listed. They reach the repository rather than being applied to the
     * finished page because {@code Page} carries its own counts — dropping rows
     * in Java would leave {@code totalElements} describing the unfiltered set.</p>
     */
    private <T> Page<T> listBonds(
            String search,
            Boolean isFlashNews,
            SecurityType securityType,
            CouponFrequency couponFrequency,
            String rating,
            Pageable pageable,
            UUID userId,
            boolean isCustomer,
            BondStatus status,
            Function<Bond, T> mapper) {

        // A blank rating is the client clearing the filter, not a grade to
        // match. Normalising here keeps that '' out of the query and out of the
        // analytics, so the empty string is never reported as a filter value.
        String ratingFilter = (rating == null || rating.isBlank())
                ? null
                : rating.trim();

        boolean noFilters = (search == null || search.isBlank())
                && isFlashNews == null
                && securityType == null
                && couponFrequency == null
                && ratingFilter == null;

        Page<Bond> matching;

        if (status == null) {
            matching = noFilters
                    ? bondRepository.findAll(pageable)
                    : bondRepository.searchBonds(
                            search,
                            isFlashNews,
                            securityType,
                            couponFrequency,
                            ratingFilter,
                            pageable);
        } else {
            matching = noFilters
                    ? bondRepository.findByStatus(status, pageable)
                    : bondRepository.searchBondsByStatus(
                            status,
                            search,
                            isFlashNews,
                            securityType,
                            couponFrequency,
                            ratingFilter,
                            pageable);
        }

        Page<T> bonds = matching.map(mapper);

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

            // One event per facet the caller actually set, so a filter's
            // popularity can be read off the event stream without parsing a
            // combined value out of the metadata.
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

            if (securityType != null) {
                analyticsService.track(
                        AnalyticsEventType.BOND_FILTER,
                        userId,
                        null,
                        null,
                        "WEB",
                        "BOND_LIST",
                        Map.of("filter", "securityType", "value", securityType));
            }

            if (couponFrequency != null) {
                analyticsService.track(
                        AnalyticsEventType.BOND_FILTER,
                        userId,
                        null,
                        null,
                        "WEB",
                        "BOND_LIST",
                        Map.of("filter", "couponFrequency", "value", couponFrequency));
            }

            if (ratingFilter != null) {
                analyticsService.track(
                        AnalyticsEventType.BOND_FILTER,
                        userId,
                        null,
                        null,
                        "WEB",
                        "BOND_LIST",
                        Map.of("filter", "rating", "value", ratingFilter));
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
                                "filterApplied", isFlashNews != null
                                        || securityType != null
                                        || couponFrequency != null
                                        || ratingFilter != null));
            }
        }

        return bonds;
    }

    // =========================================================
    // UPDATE
    // =========================================================

    public BondResponse updateBond(
            String isin,
            UpdateBondRequest request) {

        log.info(
                "Updating bond: isin={}, price={}, annualYtm={}, calculationType={}",
                isin,
                request.getPrice(),
                request.getAnnualYtm(),
                request.getCalculationType());

        Bond bond = getbondByIs(isin);

        log.info(
                "Bond loaded for update: isin={}, bondId={}, status={}, calculationType={}",
                bond.getIsin(),
                bond.getId(),
                bond.getStatus(),
                bond.getCalculationType());

        // -----------------------------------------------------
        // Matured bond cannot be modified
        // -----------------------------------------------------

        if (bond.getStatus() == BondStatus.MATURED) {

            log.warn(
                    "Rejecting update for matured bond: isin={}, bondId={}",
                    bond.getIsin(),
                    bond.getId());

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

        log.info(
                "Bond fields applied: isin={}, bondId={}, name={}, couponRate={}, "
                        + "maturityDate={}, quantum={}",
                bond.getIsin(),
                bond.getId(),
                bond.getName(),
                bond.getCouponRate(),
                bond.getMaturityDate(),
                bond.getQuantum());

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

            log.info(
                    "Re-deriving price/yield pair: isin={}, bondId={}, "
                            + "calculationType={}",
                    bond.getIsin(),
                    bond.getId(),
                    bond.getCalculationType());

            bondValuationService.applyCalculationType(bond, LocalDate.now());

            log.info(
                    "Price/yield pair derived: isin={}, bondId={}, price={}, "
                            + "semiYtm={}, annualYtm={}, ytc={}, ytmCalculatedAt={}",
                    bond.getIsin(),
                    bond.getId(),
                    bond.getPrice(),
                    bond.getSemiYtm(),
                    bond.getAnnualYtm(),
                    bond.getYtc(),
                    bond.getYtmCalculatedAt());

        } else {

            log.info(
                    "Skipping price/yield derivation, request moved neither: "
                            + "isin={}, bondId={}",
                    bond.getIsin(),
                    bond.getId());
        }

        // -----------------------------------------------------
        // Save
        // -----------------------------------------------------

        log.info("Evicting cached bond before save: isin={}", isin);

        evictCachedBond(isin);

        Bond savedBond = bondRepository.save(bond);

        log.info(
                "Bond updated successfully: isin={}, bondId={}, status={}",
                savedBond.getIsin(),
                savedBond.getId(),
                savedBond.getStatus());

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
     * Looks a bond up by its identifier.
     *
     * <p>Used where the caller already holds the id — typically to reload after
     * a write that went straight to the database, where the entity in memory is
     * known to be stale.</p>
     *
     * @param bondId bond identifier
     * @return the bond
     * @throws ResourceNotFoundException when no such bond exists
     */
    public Bond findBondById(UUID bondId) {

        return bondRepository.findById(bondId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bond not found with id: " + bondId));
    }

    /**
     * Persists a bond and drops its cached read.
     *
     * <p>The single write path for callers outside this class. Eviction happens
     * first, as it does for every write here: a failed eviction should leave the
     * transaction rolled back and the cache still describing the row that
     * survived, rather than pairing a committed change with a stale entry.</p>
     *
     * @param bond bond to persist
     * @return the persisted bond
     */
    public Bond saveBond(Bond bond) {

        evictCachedBond(bond.getIsin());

        return bondRepository.save(bond);
    }

    /**
     * Reserves units from a bond's quantum — the oversell guard.
     *
     * <p>Delegates to the repository's single conditional UPDATE, which is what
     * makes two concurrent buyers serialise in the database instead of both
     * reading the same quantity and then overwriting each other.</p>
     *
     * @param bondId   bond whose quantum is being reserved
     * @param quantity amount to deduct
     * @return 1 when the units were reserved, 0 when the bond had fewer left
     */
    public int reserveQuantity(UUID bondId, BigDecimal quantity) {
        return bondRepository.reserveQuantity(bondId, quantity);
    }

    /**
     * The ISINs of every bond belonging to an issuer.
     *
     * <p>Needed when something the bond responses embed changes per issuer — a
     * logo, for instance — so every affected cached bond can be evicted.</p>
     *
     * @param issuerId issuer identifier
     * @return the ISINs of that issuer's bonds
     */
    public List<String> findIsinsByIssuerId(UUID issuerId) {
        return bondRepository.findIsinsByIssuerId(issuerId);
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

    /**
     * The summary a list is built from — see {@link BondResponse}.
     *
     * <p>Also what a write returns, because the caller that just created or
     * patched a bond already knows what it sent, and a fresh copy of every field
     * buys it nothing.</p>
     */
    private BondResponse mapToResponse(Bond bond) {

        return BondResponse.builder()

                .name(
                        bond.getName())

                .isin(
                        bond.getIsin())

                .securityType(
                        bond.getSecurityType())

                .couponRate(
                        bond.getCouponRate())

                .couponFrequency(
                        bond.getCouponFrequency())

                .annualYtm(
                        bond.getAnnualYtm())

                .maturityType(
                        bond.getMaturityType())

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

                .logoUrl(
                        logoUrl(bond))

                .build();
    }

    /**
     * Where the bond's logo can be fetched, or null when it has none.
     *
     * <p>When a public base URL is configured for the bucket, this is the
     * absolute URL of the object on that custom domain — e.g.
     * {@code https://assets.example.com/bonds/ine123a01011/logo/...png}. A
     * browser fetches it straight from the bucket's edge, with no redirect
     * through this application and no per-request signing, which is what makes
     * a catalogue of cards cheap to render.</p>
     *
     * <p>Without that configuration it falls back to this application's own
     * endpoint, which redirects to a freshly signed URL. The key names an
     * object in a private bucket, so neither form can be built from the key
     * alone.</p>
     *
     * <p>The logo belongs to the bond's <em>issuer</em>, not to the bond: every
     * bond of one issuer shows the same image, so it is stored once on
     * {@link com.click4bonds.app.Modules.Bond.Models.Issuer#getLogoKey()} and
     * each bond response reads it from there. The read joins the issuer, and
     * this method is the only thing the response takes from that join.</p>
     *
     * <p>The public form is built from the issuer's stored key, and the
     * fallback from the bond's own ISIN, rather than from anything the caller
     * sent, so the value is always one this application meant to expose.</p>
     */
    private String logoUrl(Bond bond) {

        String logoKey = bond.getIssuer() == null
                ? null
                : bond.getIssuer().getLogoKey();

        if (logoKey == null || logoKey.isBlank()) {
            return null;
        }

        String publicBaseUrl = storageProperties.getR2().getPublicBaseUrl();

        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {

            return joinUrl(publicBaseUrl, logoKey);
        }

        return "/api/bonds/" + bond.getIsin() + "/logo";
    }

    /**
     * Joins a base URL and an object key with exactly one slash between them.
     *
     * <p>Configuration tends to arrive with a trailing slash and a key may
     * start with one; neither is guaranteed, and both together would produce a
     * {@code //} the bucket does not serve.</p>
     */
    private static String joinUrl(String baseUrl, String key) {

        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        String path = key.startsWith("/") ? key.substring(1) : key;

        return base + "/" + path;
    }

    /**
     * The bond in full — see {@link BondDetailResponse}.
     *
     * <p>Everything the entity holds that is about the bond is copied across,
     * with the raw text columns left as they were entered and the normalized
     * values that were parsed from them alongside. Nothing is derived here: a
     * detail view is the record, and a reader wanting a figure it can compute
     * has the inputs for it.</p>
     */
    private BondDetailResponse mapToDetail(Bond bond) {

        BondDetailResponse.BondDetailResponseBuilder<?, ?> builder =
                BondDetailResponse.builder();

        applyDetail(builder, bond);

        return builder.build();
    }

    /**
     * The bond in full as an admin sees it — see {@link AdminBondDetailResponse}.
     *
     * <p>The public detail plus the provenance columns. Both views are built by
     * {@link #applyDetail}, so the fields they share are listed once and cannot
     * fall out of step; the three lines below are the entire difference between
     * them.</p>
     */
    private AdminBondDetailResponse mapToAdminDetail(Bond bond) {

        AdminBondDetailResponse.AdminBondDetailResponseBuilder<?, ?> builder =
                AdminBondDetailResponse.builder();

        applyDetail(builder, bond);

        builder.sourceFileName(bond.getSourceFileName())
                .sourceRowNumber(bond.getSourceRowNumber())
                .importedAt(bond.getImportedAt());

        return builder.build();
    }

    /**
     * Copies the fields both detail views carry onto {@code builder}.
     *
     * <p>Takes the builder rather than returning a value so the one field list
     * can feed either response type: the admin builder is a subtype of this
     * one, so it is accepted here and keeps its own extra fields set by the
     * caller.</p>
     */
    private void applyDetail(
            BondDetailResponse.BondDetailResponseBuilder<?, ?> builder,
            Bond bond) {

        builder

                .id(
                        bond.getId())

                .serialNumber(
                        bond.getSerialNumber())

                .name(
                        bond.getName())

                .isin(
                        bond.getIsin())

                // Classification
                .category(
                        bond.getCategory())

                .securityType(
                        bond.getSecurityType())

                .rating(
                        bond.getRating())

                .ratingAgency(
                        bond.getRatingAgency())

                // Coupon
                .couponRate(
                        bond.getCouponRate())

                .couponFrequency(
                        bond.getCouponFrequency())

                .ipDateDescription(
                        bond.getIpDateDescription())

                // Record date rule (source of truth for coupon entitlement)
                .recordDate(
                        bond.getRecordDate())

                // Maturity
                .maturityType(
                        bond.getMaturityType())

                .maturityDate(
                        bond.getMaturityDate())

                .maturityDescription(
                        bond.getMaturityDescription())

                .putCallDescription(
                        bond.getPutCallDescription())

                // Market
                .calculationType(
                        bond.getCalculationType())

                .price(
                        bond.getPrice())

                // Yield
                .semiYtm(
                        bond.getSemiYtm())

                .annualYtm(
                        bond.getAnnualYtm())

                .ytc(
                        bond.getYtc())

                .ytmCalculatedAt(
                        bond.getYtmCalculatedAt())

                // Quantum
                .quantumDescription(
                        bond.getQuantumDescription())

                .quantum(
                        bond.getQuantum())

                .maxNoOfBonds(
                        maxNoOfBonds(bond))

                // Lot
                .lotSizeDescription(
                        bond.getLotSizeDescription())

                .lotSize(
                        bond.getLotSize())

                .lotSizeType(
                        bond.getLotSizeType())

                .isFlashNews(
                        bond.getIsFlashNews())

                .logoUrl(
                        logoUrl(bond))

                // Status
                .status(
                        bond.getStatus())

                // Audit
                .createdAt(
                        bond.getCreatedAt())

                .updatedAt(
                        bond.getUpdatedAt());
    }

    private Integer maxNoOfBonds(Bond bond) {

        if (bond.getQuantum() == null
                || bond.getLotSize() == null
                || bond.getLotSize().signum() <= 0) {
            return null;
        }

        return bond.getQuantum()
                .divideToIntegralValue(bond.getLotSize())
                .intValueExact();
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