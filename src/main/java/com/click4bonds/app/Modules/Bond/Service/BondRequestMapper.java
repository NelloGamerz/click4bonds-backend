package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.CreateBondRequest;
import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;
import com.click4bonds.app.Modules.Bond.Dto.ParsedLotSize;
import com.click4bonds.app.Modules.Bond.Dto.UpdateBondRequest;
import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Enums.CouponFrequency;
import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;
import com.click4bonds.app.Modules.Bond.Enums.MaturityType;
import com.click4bonds.app.Modules.Bond.Enums.SecurityType;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.User.Model.User;

import lombok.RequiredArgsConstructor;

/**
 * Copies a bond create/update request onto the {@code Bond} entity, parsing
 * every loosely-typed value on the way through {@link BondFieldParser}.
 *
 * <p>
 * Parsing lives here rather than in {@link BondService} so the service stays a
 * thin orchestrator and the "raw text in, normalized columns out" rule has one
 * home that can be read end-to-end and tested on its own.
 */
@Service
@RequiredArgsConstructor
public class BondRequestMapper {

    private final BondFieldParser fieldParser;

    // =========================================================
    // CREATE
    // =========================================================

    /**
     * Builds a new DRAFT bond from a create request.
     *
     * @throws BadRequestException when a required value is missing or a supplied
     *                             value cannot be parsed
     */
    public Bond toEntity(CreateBondRequest request, User admin) {

        ParsedMaturity maturity = resolveMaturity(
                request.getMaturityDescription(),
                request.getMaturityType(),
                request.getMaturityDate());

        if (maturity.schedule() == null) {
            throw new BadRequestException(
                    "A maturity description or maturity date is required");
        }

        BigDecimal couponRate = fieldParser.parsePercentage(
                request.getCouponRate(),
                "couponRate");

        requireNonNegative(couponRate, "couponRate");

        BigDecimal price = parseOptionalDecimal(request.getPrice(), "price");

        requirePositiveIfPresent(price, "price");

        CouponFrequency couponFrequency = resolveCouponFrequency(
                request.getCouponFrequency(),
                request.getIpDateDescription());

        ParsedQuantum quantum = resolveQuantum(
                request.getQuantumDescription(),
                request.getQuantumInLacs());

        ParsedLot lot = resolveLot(
                request.getLotSizeDescription(),
                request.getLotSize(),
                request.getLotSizeType());

        YtmValues ytm = parseYtm(
                request.getSemiYtm(),
                request.getAnnualYtm(),
                request.getYtc());

        return Bond.builder()

                .serialNumber(request.getSerialNumber())

                .name(request.getName().trim())

                .isin(normalizeIsin(request.getIsin()))

                // Classification
                .category(trimToNull(request.getCategory()))
                .securityType(fieldParser.parseSecurityType(request.getSecurityType()))
                .rating(trimToNull(request.getRating()))
                .ratingAgency(trimToNull(request.getRatingAgency()))

                // Coupon
                .couponRate(couponRate)
                .couponFrequency(couponFrequency)
                .ipDateDescription(trimToNull(request.getIpDateDescription()))

                // Record date rule (source of truth for coupon entitlement)
                .recordDateDescription(trimToNull(request.getRecordDateDescription()))

                // Maturity
                .maturityType(maturity.type())
                .maturityDate(maturity.date())
                .maturityDescription(trimToNull(request.getMaturityDescription()))

                // Put / Call
                .putCallDescription(trimToNull(request.getPutCallDescription()))

                // Market
                .price(price)

                // YTM supplied by the caller. It is overwritten the next time the
                // engine calculates a yield from the price.
                .semiYtm(ytm.semiYtm())
                .annualYtm(ytm.annualYtm())
                .ytc(ytm.ytc())
                .ytmCalculatedAt(ytm.anySupplied() ? Instant.now() : null)

                // Quantum
                .quantumDescription(trimToNull(request.getQuantumDescription()))
                .quantumInLacs(quantum.inLacs())

                // Lot
                .lotSizeDescription(trimToNull(request.getLotSizeDescription()))
                .lotSize(lot.size())
                .lotSizeType(lot.type())

                // Inventory
                .remainingQuantity(request.getRemainingQuantity())

                // Flags
                .isFlashNews(Boolean.TRUE.equals(request.getIsFlashNews()))

                // Status
                .status(BondStatus.DRAFT)

                // Audit
                .createdBy(admin)

                .build();
    }

    // =========================================================
    // UPDATE
    // =========================================================

    /**
     * Applies the non-null fields of an update request to an existing bond.
     *
     * <p>
     * A null field means "leave untouched", matching the previous behaviour. An
     * unparseable non-null field is rejected rather than stored.
     */
    public void applyUpdate(Bond bond, UpdateBondRequest request) {

        // -----------------------------------------------------
        // Basic information
        // -----------------------------------------------------

        if (request.getSerialNumber() != null) {
            bond.setSerialNumber(request.getSerialNumber());
        }

        if (request.getName() != null) {
            bond.setName(request.getName().trim());
        }

        // -----------------------------------------------------
        // Classification
        // -----------------------------------------------------

        if (request.getCategory() != null) {
            bond.setCategory(trimToNull(request.getCategory()));
        }

        if (request.getSecurityType() != null) {
            bond.setSecurityType(fieldParser.parseSecurityType(request.getSecurityType()));
        }

        if (request.getRating() != null) {
            bond.setRating(trimToNull(request.getRating()));
        }

        if (request.getRatingAgency() != null) {
            bond.setRatingAgency(trimToNull(request.getRatingAgency()));
        }

        // -----------------------------------------------------
        // Coupon
        // -----------------------------------------------------

        if (request.getCouponRate() != null) {

            BigDecimal couponRate = fieldParser.parsePercentage(
                    request.getCouponRate(),
                    "couponRate");

            requireNonNegative(couponRate, "couponRate");

            bond.setCouponRate(couponRate);
        }

        if (request.getIpDateDescription() != null) {

            bond.setIpDateDescription(trimToNull(request.getIpDateDescription()));

            CouponFrequency frequency = resolveCouponFrequency(
                    request.getCouponFrequency(),
                    request.getIpDateDescription());

            if (frequency != null) {
                bond.setCouponFrequency(frequency);
            }

        } else if (request.getCouponFrequency() != null) {

            bond.setCouponFrequency(
                    fieldParser.parseCouponFrequency(request.getCouponFrequency()));
        }

        // -----------------------------------------------------
        // Record date rule (source of truth for entitlement)
        // -----------------------------------------------------

        if (request.getRecordDateDescription() != null) {
            bond.setRecordDateDescription(trimToNull(request.getRecordDateDescription()));
        }

        // -----------------------------------------------------
        // Maturity
        // -----------------------------------------------------

        applyMaturityUpdate(bond, request);

        // -----------------------------------------------------
        // Put / Call
        // -----------------------------------------------------

        if (request.getPutCallDescription() != null) {
            bond.setPutCallDescription(trimToNull(request.getPutCallDescription()));
        }

        // -----------------------------------------------------
        // Price
        // -----------------------------------------------------

        if (request.getPrice() != null) {

            BigDecimal price = fieldParser.parseDecimal(request.getPrice(), "price");

            requirePositiveIfPresent(price, "price");

            bond.setPrice(price);

            /*
             * Price changed, so a previously calculated yield is now stale.
             * A yield supplied in this same request is applied afterwards, below.
             */
            invalidateYield(bond);
        }

        // -----------------------------------------------------
        // Quantum
        // -----------------------------------------------------

        if (request.getQuantumDescription() != null) {

            bond.setQuantumDescription(trimToNull(request.getQuantumDescription()));

            bond.setQuantumInLacs(
                    resolveQuantum(
                            request.getQuantumDescription(),
                            request.getQuantumInLacs()).inLacs());

        } else if (request.getQuantumInLacs() != null) {

            bond.setQuantumInLacs(
                    fieldParser.parseDecimal(request.getQuantumInLacs(), "quantumInLacs"));
        }

        // -----------------------------------------------------
        // Lot
        // -----------------------------------------------------

        if (request.getLotSizeDescription() != null) {

            bond.setLotSizeDescription(trimToNull(request.getLotSizeDescription()));

            ParsedLot lot = resolveLot(
                    request.getLotSizeDescription(),
                    request.getLotSize(),
                    request.getLotSizeType());

            bond.setLotSize(lot.size());
            bond.setLotSizeType(lot.type());

        } else {

            if (request.getLotSize() != null) {
                bond.setLotSize(
                        fieldParser.parseDecimal(request.getLotSize(), "lotSize"));
            }

            if (request.getLotSizeType() != null) {
                bond.setLotSizeType(
                        fieldParser.parseEnum(
                                LotSizeType.class,
                                request.getLotSizeType(),
                                "lotSizeType"));
            }
        }

        // -----------------------------------------------------
        // Yield (applied last, so it survives the price invalidation above)
        // -----------------------------------------------------

        YtmValues ytm = parseYtm(
                request.getSemiYtm(),
                request.getAnnualYtm(),
                request.getYtc());

        if (ytm.anySupplied()) {

            if (ytm.semiYtm() != null) {
                bond.setSemiYtm(ytm.semiYtm());
            }

            if (ytm.annualYtm() != null) {
                bond.setAnnualYtm(ytm.annualYtm());
            }

            if (ytm.ytc() != null) {
                bond.setYtc(ytm.ytc());
            }

            bond.setYtmCalculatedAt(Instant.now());
        }

        // -----------------------------------------------------
        // Inventory
        // -----------------------------------------------------

        /*
         * Absolute value, not a delta: an admin restocking a bond sends the new
         * total. Status is deliberately left alone — a restocked SOLD_OUT bond
         * has to be activated through the existing activate endpoint, so the
         * inventory and the status never disagree silently.
         */
        if (request.getRemainingQuantity() != null) {
            bond.setRemainingQuantity(request.getRemainingQuantity());
        }

        if (request.getIsFlashNews() != null) {
            bond.setIsFlashNews(request.getIsFlashNews());
        }
    }

    // =========================================================
    // MATURITY
    // =========================================================

    /**
     * Resolves maturity date, type and schedule together, because the date and
     * the type both come out of the same text.
     *
     * <p>
     * The description is the source of truth: when it carries a maturity, its
     * date and type are used even if the client also sent the normalized
     * companions. Those companions are read only to fill a gap — no description
     * (or one that says nothing, such as {@code NA}).
     */
    private ParsedMaturity resolveMaturity(
            String maturityDescription,
            String explicitType,
            String explicitDate) {

        LocalDate requestedDate = explicitDate == null || explicitDate.isBlank()
                ? null
                : fieldParser.parseDate(explicitDate, "maturityDate");

        if (hasMeaningfulText(maturityDescription)) {

            MaturitySchedule schedule = fieldParser.parseMaturity(
                    maturityDescription,
                    requestedDate);

            return new ParsedMaturity(
                    schedule,
                    schedule.maturityDate(),
                    fieldParser.deriveMaturityType(schedule, maturityDescription));
        }

        MaturitySchedule schedule = fieldParser.parseMaturity(null, requestedDate);

        MaturityType type;

        if (explicitType != null && !explicitType.isBlank()) {

            type = fieldParser.parseMaturityType(explicitType);

            if (type == MaturityType.PERPETUAL && schedule == null) {
                schedule = new MaturitySchedule(null, List.of(), true);
            }

        } else {

            type = fieldParser.deriveMaturityType(schedule, null);
        }

        return new ParsedMaturity(schedule, requestedDate, type);
    }

    /**
     * Maturity is applied as a unit. When the request carries a description, the
     * description owns the date and the type — setting both, including clearing
     * them when it describes a perpetual bond or says nothing. A request with
     * only a date or a type updates just that field.
     */
    private void applyMaturityUpdate(Bond bond, UpdateBondRequest request) {

        boolean touched = request.getMaturityDescription() != null
                || request.getMaturityDate() != null
                || request.getMaturityType() != null;

        if (!touched) {
            return;
        }

        ParsedMaturity maturity = resolveMaturity(
                request.getMaturityDescription(),
                request.getMaturityType(),
                request.getMaturityDate());

        if (request.getMaturityDescription() != null) {

            bond.setMaturityDescription(trimToNull(request.getMaturityDescription()));
            bond.setMaturityDate(maturity.date());
            bond.setMaturityType(maturity.type());

            return;
        }

        if (maturity.date() != null) {
            bond.setMaturityDate(maturity.date());
        }

        if (maturity.type() != null) {
            bond.setMaturityType(maturity.type());
        }
    }

    // =========================================================
    // COUPON FREQUENCY
    // =========================================================

    /**
     * The schedule grammar in the IP-date description wins, because it is what
     * the cash-flow engine will read. An explicit frequency is used when the
     * description is not one of the supported schedules, and keyword inference
     * is the last resort.
     */
    private CouponFrequency resolveCouponFrequency(
            String explicitFrequency,
            String ipDateDescription) {

        CouponFrequency fromSchedule = fieldParser.frequencyFromSchedule(ipDateDescription);

        if (fromSchedule != null) {
            return fromSchedule;
        }

        if (explicitFrequency != null && !explicitFrequency.isBlank()) {
            return fieldParser.parseCouponFrequency(explicitFrequency);
        }

        return fieldParser.parseCouponFrequency(ipDateDescription);
    }

    // =========================================================
    // QUANTUM / LOT
    // =========================================================

    /**
     * The description is the source of truth. {@code "Any"} and {@code "1
     * Bonds"} legitimately mean "no numeric quantum", so an explicit value is
     * not used to fill those in — only an absent description falls back to it.
     */
    private ParsedQuantum resolveQuantum(String description, String explicitInLacs) {

        if (description != null && !description.isBlank()) {
            return new ParsedQuantum(fieldParser.parseQuantumInLacs(description));
        }

        if (explicitInLacs != null && !explicitInLacs.isBlank()) {
            return new ParsedQuantum(
                    fieldParser.parseDecimal(explicitInLacs, "quantumInLacs"));
        }

        return new ParsedQuantum(null);
    }

    /**
     * The description is the source of truth. An unrecognized wording yields
     * nothing, and only then do the explicit companions apply.
     */
    private ParsedLot resolveLot(String description, String explicitSize, String explicitType) {

        if (description != null && !description.isBlank()) {

            ParsedLotSize derived = fieldParser.parseLotSize(description);

            boolean derivedSomething = derived.lotSize() != null
                    || derived.lotSizeType() != LotSizeType.UNKNOWN;

            if (derivedSomething) {
                return new ParsedLot(derived.lotSize(), derived.lotSizeType());
            }
        }

        BigDecimal size = explicitSize != null && !explicitSize.isBlank()
                ? fieldParser.parseDecimal(explicitSize, "lotSize")
                : null;

        LotSizeType type = explicitType != null && !explicitType.isBlank()
                ? fieldParser.parseEnum(LotSizeType.class, explicitType, "lotSizeType")
                : LotSizeType.UNKNOWN;

        return new ParsedLot(size, type);
    }

    /**
     * True when the text actually says something, so that blanks and the
     * "not applicable" markers the sheets use ({@code NA}, {@code N/A},
     * {@code -}) are treated as "no description" rather than as input the
     * parser should reject.
     */
    private boolean hasMeaningfulText(String value) {

        if (value == null || value.isBlank()) {
            return false;
        }

        return !value.trim().matches("(?i)n\\.?/?a\\.?|-");
    }

    // =========================================================
    // YIELD
    // =========================================================

    private YtmValues parseYtm(String semiYtm, String annualYtm, String ytc) {

        boolean anySupplied = (semiYtm != null && !semiYtm.isBlank())
                || (annualYtm != null && !annualYtm.isBlank())
                || (ytc != null && !ytc.isBlank());

        return new YtmValues(
                parseOptionalPercentage(semiYtm, "semiYtm"),
                parseOptionalPercentage(annualYtm, "annualYtm"),
                parseOptionalPercentage(ytc, "ytc"),
                anySupplied);
    }

    private void invalidateYield(Bond bond) {

        bond.setSemiYtm(null);
        bond.setAnnualYtm(null);
        bond.setYtc(null);
        bond.setYtmCalculatedAt(null);
    }

    // =========================================================
    // SMALL HELPERS
    // =========================================================

    private BigDecimal parseOptionalDecimal(String raw, String field) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        return fieldParser.parseDecimal(raw, field);
    }

    private BigDecimal parseOptionalPercentage(String raw, String field) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        return fieldParser.parsePercentage(raw, field);
    }

    private void requireNonNegative(BigDecimal value, String field) {

        if (value.signum() < 0) {
            throw new BadRequestException(field + " cannot be negative");
        }
    }

    private void requirePositiveIfPresent(BigDecimal value, String field) {

        if (value != null && value.signum() <= 0) {
            throw new BadRequestException(field + " must be greater than zero");
        }
    }

    private String trimToNull(String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeIsin(String isin) {

        return isin == null
                ? null
                : isin.trim().toUpperCase(java.util.Locale.ENGLISH);
    }

    // =========================================================
    // INTERNAL VALUE SHAPES
    // =========================================================

    private record ParsedMaturity(MaturitySchedule schedule, LocalDate date, MaturityType type) {
    }

    private record ParsedQuantum(BigDecimal inLacs) {
    }

    private record ParsedLot(BigDecimal size, LotSizeType type) {
    }

    private record YtmValues(
            BigDecimal semiYtm,
            BigDecimal annualYtm,
            BigDecimal ytc,
            boolean anySupplied) {
    }
}
