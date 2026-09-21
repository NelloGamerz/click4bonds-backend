package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Dto.PurchaseConsideration;
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

        /*
         * The purchase leg needs the projected coupons, so it is built after
         * them: only then is the first future coupon (and therefore the
         * settlement convention) known.
         */
        BigDecimal accruedInterest = accruedInterestService.calculate(bond, calculationDate);

        PurchaseConsideration purchase = purchaseConsiderationService.determine(
                bond,
                calculationDate,
                accruedInterest,
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

        Map<LocalDate, BigDecimal> cashFlowsByDate = new TreeMap<>();
        cashFlowsByDate.put(calculationDate, purchase.purchaseCashFlow());

        mergePositiveCashFlows(cashFlowsByDate, bond, couponPayments, principalRepayments, calculationDate);

        return cashFlowsByDate.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> new XirrCalculator.CashFlow(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void mergePositiveCashFlows(
            Map<LocalDate, BigDecimal> cashFlowsByDate,
            Bond bond,
            List<CouponPayment> couponPayments,
            List<PrincipalRepayment> principalRepayments,
            LocalDate calculationDate) {

        for (CouponPayment payment : safeList(couponPayments)) {
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
            cashFlowsByDate.merge(paymentDate, payment.couponAmount(), BigDecimal::add);
        }

        for (PrincipalRepayment repayment : safeList(principalRepayments)) {
            if (repayment == null || repayment.date() == null || !repayment.date().isAfter(calculationDate)) {
                continue;
            }
            cashFlowsByDate.merge(repayment.date(), repayment.principalAmount(), BigDecimal::add);
        }
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
        if (bond.getPrice() == null) {
            throw new IllegalArgumentException("Bond price is required");
        }
        if (bond.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bond price must be positive");
        }
    }
}
