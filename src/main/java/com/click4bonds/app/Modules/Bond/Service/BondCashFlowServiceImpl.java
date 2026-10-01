package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowEntry;
import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowResponse;
import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Dto.PurchaseConsideration;
import com.click4bonds.app.Modules.Bond.Enums.BondCashFlowType;
import com.click4bonds.app.Modules.Bond.Models.Bond;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds the cash-flow series that {@link XirrCalculator} turns into a YTM.
 *
 * <p>
 * The series is:
 *
 * <pre>
 * calculationDate  -> -purchase consideration
 * coupon dates     -> +coupon
 * principal dates  -> +principal
 * </pre>
 *
 * <h2>Record dates</h2>
 *
 * <p>
 * A record date is an entitlement reference date, <strong>not</strong> a cash
 * movement. It is never added to the series and never replaces a coupon date.
 * It determines two separate things, each owned by its own collaborator:
 *
 * <pre>
 * coupon entitlement  -&gt; CouponEntitlementService     (is the coupon received?)
 * purchase treatment  -&gt; PurchaseConsiderationService (cum or ex interest?)
 * </pre>
 *
 * <h2>Cum-interest vs ex-interest</h2>
 *
 * <p>
 * The purchase leg is built <em>after</em> the coupon projection, because the
 * treatment depends on the first future coupon and that coupon's own record
 * date. A purchase after that record date is ex-interest: the coupon belongs to
 * the seller, so the accrued interest on it is not charged either.
 *
 * <pre>
 * CUM_INTEREST : calculationDate -&gt; -(clean price + accrued interest)
 * EX_INTEREST  : calculationDate -&gt; -clean price
 * </pre>
 *
 * <p>
 * With monthly coupons the bond moves in and out of the ex-interest window once
 * per period, so the treatment is derived per calculation date from the
 * upcoming coupon rather than from a bond-level flag.
 *
 * <p>
 * When {@code Bond.recordDateDescription} is absent or {@code "NA"}, the parser
 * returns no record date, no bond is classified as ex-interest, and every
 * coupon after the calculation date is included exactly as it was before this
 * feature existed.
 */
@Service
@Slf4j
public class BondCashFlowServiceImpl implements BondCashFlowService {

    private final CouponDateGenerator couponDateGenerator;
    private final PrincipalRepaymentService principalRepaymentService;
    private final CouponCalculationService couponCalculationService;
    private final AccruedInterestService accruedInterestService;
    private final RecordDateParser recordDateParser;
    private final CouponEntitlementService couponEntitlementService;
    private final PurchaseConsiderationService purchaseConsiderationService;

    /**
     * Production constructor.
     *
     * <p>
     * {@code @Autowired} is required because a second, convenience constructor
     * exists below for callers that do not care about record dates.
     */
    @Autowired
    public BondCashFlowServiceImpl(
            CouponDateGenerator couponDateGenerator,
            PrincipalRepaymentService principalRepaymentService,
            CouponCalculationService couponCalculationService,
            AccruedInterestService accruedInterestService,
            RecordDateParser recordDateParser,
            CouponEntitlementService couponEntitlementService,
            PurchaseConsiderationService purchaseConsiderationService) {

        this.couponDateGenerator = couponDateGenerator;
        this.principalRepaymentService = principalRepaymentService;
        this.couponCalculationService = couponCalculationService;
        this.accruedInterestService = accruedInterestService;
        this.recordDateParser = recordDateParser;
        this.couponEntitlementService = couponEntitlementService;
        this.purchaseConsiderationService = purchaseConsiderationService;
    }

    /**
     * Convenience constructor using the default record-date collaborators.
     *
     * <p>
     * All three defaults are dependency-free, so no Spring context is needed.
     * Bonds without a record-date description behave identically whichever
     * constructor is used.
     */
    public BondCashFlowServiceImpl(
            CouponDateGenerator couponDateGenerator,
            PrincipalRepaymentService principalRepaymentService,
            CouponCalculationService couponCalculationService,
            AccruedInterestService accruedInterestService) {

        this(
                couponDateGenerator,
                principalRepaymentService,
                couponCalculationService,
                accruedInterestService,
                new RecordDateParserImpl(),
                new CouponEntitlementServiceImpl(),
                createDefaultPurchaseConsiderationService());
    }

    private static PurchaseConsiderationService createDefaultPurchaseConsiderationService() {
        return new PurchaseConsiderationServiceImpl(
                new RecordDateParserImpl(),
                new CouponEntitlementServiceImpl());
    }

    @Override
    public List<XirrCalculator.CashFlow> generateCashFlows(
            Bond bond,
            LocalDate calculationDate) {

        validateInput(bond, calculationDate);
        validatePrice(bond);

        BondSchedule schedule = buildSchedule(bond, calculationDate);

        Map<LocalDate, BigDecimal> cashFlowsByDate = new TreeMap<>();

        schedule.purchase().ifPresent(purchase ->
                cashFlowsByDate.put(calculationDate, purchase.purchaseCashFlow()));

        for (BondCashFlowEntry entry : schedule.entries()) {
            cashFlowsByDate.merge(entry.date(), entry.amount(), BigDecimal::add);
        }

        return cashFlowsByDate.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> new XirrCalculator.CashFlow(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public BondCashFlowResponse generateSchedule(
            Bond bond,
            LocalDate calculationDate) {

        BondSchedule schedule = buildSchedule(bond, calculationDate);

        List<BondCashFlowEntry> entries = new ArrayList<>();

        /*
         * The purchase leg leads the schedule: it is the one outflow, and it
         * carries the earliest date. A coupon falling on the calculation date
         * keeps its own row directly after it rather than being netted off.
         */
        schedule.purchase().ifPresent(purchase -> entries.add(
                new BondCashFlowEntry(
                        calculationDate,
                        BondCashFlowType.PURCHASE,
                        null,
                        null,
                        purchase.purchaseCashFlow(),
                        null
                )
        ));

        entries.addAll(schedule.entries());

        // Stable sort, so the purchase row stays first on a shared date.
        entries.sort(Comparator.comparing(BondCashFlowEntry::date));

        return new BondCashFlowResponse(
                bond.getIsin(),
                bond.getName(),
                calculationDate,
                schedule.purchase()
                        .map(PurchaseConsideration::purchaseConsideration)
                        .orElse(null),
                total(entries, BondCashFlowEntry::couponAmount),
                total(entries, BondCashFlowEntry::principalAmount),
                total(entries, BondCashFlowEntry::amount),
                List.copyOf(entries)
        );
    }

    /**
     * Projects the coupons and principal repayments once, so the XIRR series and
     * the published schedule can never disagree about what a bond pays.
     */
    private BondSchedule buildSchedule(Bond bond, LocalDate calculationDate) {

        List<LocalDate> couponDates = safeList(
                couponDateGenerator.generate(bond, calculationDate)
        );
        List<PrincipalRepayment> principalRepayments = safeList(
                principalRepaymentService.generateRepayments(
                        bond,
                        couponDates,
                        calculationDate
                )
        );
        List<CouponPayment> couponPayments = safeList(
                couponCalculationService.calculateCoupons(
                        bond,
                        couponDates,
                        principalRepayments,
                        calculationDate
                )
        );

        Optional<PurchaseConsideration> purchase = buildPurchaseConsideration(
                bond,
                calculationDate,
                couponPayments
        );

        Map<LocalDate, CashFlowRow> rows = new TreeMap<>();

        mergeCoupons(rows, bond, couponPayments, calculationDate);
        mergePrincipalRepayments(rows, principalRepayments, calculationDate);

        return new BondSchedule(
                purchase,
                rows.values().stream().map(CashFlowRow::toEntry).toList()
        );
    }

    /**
     * Builds the purchase leg.
     *
     * <p>
     * It needs the projected coupons, so it is built after them: only then is
     * the first future coupon (and therefore the settlement convention) known.
     *
     * <p>
     * Empty when the bond has no usable price. The XIRR series requires a price,
     * but a schedule of what the holder receives is still meaningful without
     * one, so the leg is dropped rather than the request rejected.
     */
    private Optional<PurchaseConsideration> buildPurchaseConsideration(
            Bond bond,
            LocalDate calculationDate,
            List<CouponPayment> couponPayments) {

        if (!hasUsablePrice(bond)) {

            log.debug(
                    "No usable price for isin={} - purchase leg omitted from the schedule",
                    bond.getIsin());

            return Optional.empty();
        }

        PurchaseConsideration purchase = purchaseConsiderationService.determine(
                bond,
                calculationDate,
                accruedInterestService.calculate(bond, calculationDate),
                couponPayments);

        log.debug(
                "Purchase consideration: isin={} calculationDate={} treatment={} cleanPrice={} accruedInterestCharged={} purchaseConsideration={} upcomingPaymentDate={} upcomingRecordDate={}",
                bond.getIsin(),
                calculationDate,
                purchase.treatment(),
                purchase.cleanPrice(),
                purchase.accruedInterestCharged(),
                purchase.purchaseConsideration(),
                purchase.upcomingPaymentDate(),
                purchase.upcomingRecordDate());

        return Optional.of(purchase);
    }

    private void mergeCoupons(
            Map<LocalDate, CashFlowRow> rows,
            Bond bond,
            List<CouponPayment> couponPayments,
            LocalDate calculationDate) {

        for (CouponPayment payment : couponPayments) {
            if (payment == null || payment.date() == null) {
                continue;
            }

            /*
             * The record date belongs to this payment date only. Monthly bonds
             * therefore get a fresh record date per coupon; nothing is cached or
             * reused across payments.
             */
            LocalDate paymentDate = payment.date();
            Optional<LocalDate> recordDate = recordDateParser.calculateRecordDate(bond, paymentDate);

            boolean entitled = couponEntitlementService.isCouponEntitled(
                    calculationDate,
                    recordDate.orElse(null),
                    paymentDate);

            log.debug(
                    "Coupon entitlement: isin={} paymentDate={} recordDate={} calculationDate={} couponAmount={} couponEntitled={}",
                    bond.getIsin(),
                    paymentDate,
                    recordDate.orElse(null),
                    calculationDate,
                    payment.couponAmount(),
                    entitled);

            if (!entitled) {
                continue;
            }

            /*
             * The coupon stays on its payment date. A record date must never
             * become the date of a coupon cash flow.
             */
            row(rows, paymentDate).addCoupon(
                    payment.couponAmount(),
                    payment.outstandingPrincipalBeforePayment());
        }
    }

    private void mergePrincipalRepayments(
            Map<LocalDate, CashFlowRow> rows,
            List<PrincipalRepayment> principalRepayments,
            LocalDate calculationDate) {

        for (PrincipalRepayment repayment : principalRepayments) {
            if (repayment == null || repayment.date() == null || !repayment.date().isAfter(calculationDate)) {
                continue;
            }
            row(rows, repayment.date()).addPrincipal(
                    repayment.principalAmount(),
                    repayment.remainingPrincipal());
        }
    }

    private static CashFlowRow row(Map<LocalDate, CashFlowRow> rows, LocalDate date) {
        return rows.computeIfAbsent(date, CashFlowRow::new);
    }

    private static BigDecimal total(
            List<BondCashFlowEntry> entries,
            Function<BondCashFlowEntry, BigDecimal> component) {

        return entries.stream()
                .map(component)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void validateInput(Bond bond, LocalDate calculationDate) {
        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }
        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date cannot be null");
        }
    }

    private void validatePrice(Bond bond) {
        if (bond.getPrice() == null) {
            throw new IllegalArgumentException("Bond price is required");
        }
        if (bond.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bond price must be positive");
        }
    }

    private static boolean hasUsablePrice(Bond bond) {
        return bond.getPrice() != null
                && bond.getPrice().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * One date in the projection, accumulating its components before they are
     * frozen into a {@link BondCashFlowEntry}.
     */
    private static final class CashFlowRow {

        private final LocalDate date;
        private BigDecimal coupon;
        private BigDecimal principal;
        private BigDecimal outstandingPrincipal;

        private CashFlowRow(LocalDate date) {
            this.date = date;
        }

        private void addCoupon(BigDecimal amount, BigDecimal outstandingBeforePayment) {
            if (amount == null) {
                return;
            }
            coupon = coupon == null ? amount : coupon.add(amount);
            if (outstandingBeforePayment != null) {
                outstandingPrincipal = outstandingBeforePayment;
            }
        }

        private void addPrincipal(BigDecimal amount, BigDecimal remainingPrincipal) {
            if (amount == null) {
                return;
            }
            principal = principal == null ? amount : principal.add(amount);

            /*
             * Applied after the coupon, so a date carrying both reports the
             * principal left once the repayment has gone through.
             */
            if (remainingPrincipal != null) {
                outstandingPrincipal = remainingPrincipal;
            }
        }

        private BondCashFlowEntry toEntry() {
            BigDecimal amount = zeroWhenNull(coupon).add(zeroWhenNull(principal));

            BondCashFlowType type;
            if (coupon != null && principal != null) {
                type = BondCashFlowType.COUPON_AND_PRINCIPAL;
            } else if (coupon != null) {
                type = BondCashFlowType.COUPON;
            } else {
                type = BondCashFlowType.PRINCIPAL;
            }

            return new BondCashFlowEntry(
                    date,
                    type,
                    coupon,
                    principal,
                    amount,
                    outstandingPrincipal);
        }

        private static BigDecimal zeroWhenNull(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    private record BondSchedule(
            Optional<PurchaseConsideration> purchase,
            List<BondCashFlowEntry> entries) {
    }
}
