package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Derives record dates from a bond's record-date description.
 *
 * <h2>Why this exists</h2>
 *
 * <p>
 * {@code Bond.recordDateDescription} is the <strong>source rule</strong> - the
 * text that arrived from the backend / Excel feed. A record date is
 * <strong>derived</strong> from that rule and a specific coupon payment date,
 * because a bond with monthly coupons has a different record date for every one
 * of its coupon payments.
 *
 * <p>
 * One record date therefore cannot be stored per bond. The description is the
 * source of truth and this parser is the only place that converts it into
 * dates.
 *
 * <h2>What a record date is not</h2>
 *
 * <p>
 * A record date is an entitlement reference date, not a cash movement. It never
 * moves a coupon and never becomes a cash flow of its own - see
 * {@link CouponEntitlementService} and {@link BondCashFlowService}.
 */
public interface RecordDateParser {

    /**
     * Resolves the record date for one payment date.
     *
     * <pre>
     * description = "15 days prior to interest payment date"
     * paymentDate = 2026-10-23
     * result      = 2026-10-08
     * </pre>
     *
     * @param description the raw record-date description; {@code null}, blank or
     *                    a not-applicable marker yields {@link Optional#empty()}
     * @param paymentDate the coupon / interest payment date the record date is
     *                    relative to; must not be {@code null}
     * @return the record date, or {@link Optional#empty()} when the bond has no
     *         record-date rule
     * @throws IllegalArgumentException                 when {@code paymentDate} is
     *                                                  {@code null}
     * @throws com.click4bonds.app.Modules.Bond.Exception.UnsupportedRecordDateDescriptionException
     *                                                  when the description is neither
     *                                                  not-applicable nor understood
     */
    Optional<LocalDate> parse(String description, LocalDate paymentDate);

    /**
     * Extracts the day offset without applying it to a date.
     *
     * @param description the raw record-date description
     * @return the number of days the record date falls before the payment date,
     *         or {@link Optional#empty()} when the bond has no record-date rule
     * @throws com.click4bonds.app.Modules.Bond.Exception.UnsupportedRecordDateDescriptionException
     *         when the description is neither not-applicable nor understood
     */
    Optional<Integer> parseOffsetDays(String description);

    /**
     * @param description the raw record-date description
     * @return {@code true} when this parser can derive record dates from the
     *         description, {@code false} when the bond simply has no record-date
     *         rule
     * @throws com.click4bonds.app.Modules.Bond.Exception.UnsupportedRecordDateDescriptionException
     *         when the description is neither not-applicable nor understood
     */
    boolean isApplicable(String description);

    /**
     * Convenience form of {@link #parse(String, LocalDate)} using the bond's own
     * description.
     *
     * @param bond        the bond; must not be {@code null}
     * @param paymentDate the coupon / interest payment date
     * @return the record date for that payment, or {@link Optional#empty()}
     */
    Optional<LocalDate> calculateRecordDate(Bond bond, LocalDate paymentDate);

    /**
     * Resolves the record date belonging to each supplied payment date.
     *
     * <p>
     * Every entry is computed independently, so MONTHLY, QUARTERLY, HALF_YEARLY
     * and YEARLY schedules all get their own record date rather than sharing one
     * value. Payment dates without a record-date rule are simply absent from the
     * result.
     *
     * @param bond         the bond; must not be {@code null}
     * @param paymentDates the coupon payment dates; may be {@code null}
     * @return a payment date to record date mapping in the order supplied
     */
    Map<LocalDate, LocalDate> calculateRecordDates(Bond bond, List<LocalDate> paymentDates);
}
