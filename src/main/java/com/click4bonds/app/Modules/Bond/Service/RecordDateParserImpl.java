package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Bond.Exception.UnsupportedRecordDateDescriptionException;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Default {@link RecordDateParser}.
 *
 * <p>
 * Delegates to an ordered list of {@link RecordDateRule} strategies so that new
 * record-date wordings can be added without touching this class, the cash-flow
 * generator, or the XIRR calculation.
 *
 * <p>
 * There is deliberately no fallback offset. {@code Bond.recordDate} is the
 * source rule: if the admin sent 7, 7 is used; if 10, 10 is used. A silently
 * applied default would produce a wrong record date, a wrong entitlement
 * decision and therefore a wrong YTM.
 *
 * <p>
 * The {@link RecordDateRule} strategies and the text-based methods on
 * {@link RecordDateParser} are retained for the historic free-text form of the
 * rule, but the bond path no longer uses them: a bond now carries the offset
 * itself and {@link #calculateRecordDate(Bond, LocalDate)} reads it directly.
 */
@Service
public class RecordDateParserImpl implements RecordDateParser {

    private final List<RecordDateRule> rules;

    /**
     * Spring constructor: wires the supported rule strategies in priority order.
     */
    public RecordDateParserImpl() {
        this(List.of(new RelativeDaysRecordDateRule()));
    }

    /**
     * Test / extension constructor.
     *
     * @param rules the strategies to try, in order; earlier rules win
     */
    public RecordDateParserImpl(List<RecordDateRule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    @Override
    public Optional<LocalDate> parse(String description, LocalDate paymentDate) {

        if (paymentDate == null) {
            throw new IllegalArgumentException("Payment date cannot be null");
        }

        return resolveOffsetDays(description)
                .map(paymentDate::minusDays);
    }

    @Override
    public Optional<Integer> parseOffsetDays(String description) {
        return resolveOffsetDays(description);
    }

    @Override
    public boolean isApplicable(String description) {
        return resolveOffsetDays(description).isPresent();
    }

    @Override
    public Optional<LocalDate> calculateRecordDate(Bond bond, LocalDate paymentDate) {

        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }

        if (paymentDate == null) {
            throw new IllegalArgumentException("Payment date cannot be null");
        }

        return recordDateOffset(bond)
                .map(paymentDate::minusDays);
    }

    @Override
    public Map<LocalDate, LocalDate> calculateRecordDates(
            Bond bond,
            List<LocalDate> paymentDates) {

        if (bond == null) {
            throw new IllegalArgumentException("Bond cannot be null");
        }

        Map<LocalDate, LocalDate> recordDatesByPaymentDate = new LinkedHashMap<>();

        if (paymentDates == null) {
            return recordDatesByPaymentDate;
        }

        for (LocalDate paymentDate : paymentDates) {

            if (paymentDate == null) {
                continue;
            }

            recordDateOffset(bond)
                    .map(paymentDate::minusDays)
                    .ifPresent(recordDate -> recordDatesByPaymentDate.put(paymentDate, recordDate));
        }

        return recordDatesByPaymentDate;
    }

    /**
     * The bond's record-date offset, when it has one.
     *
     * <p>
     * {@code NULL} and non-positive values both mean "no record-date rule", so
     * those bonds resolve no record date and keep their historical cum-interest
     * behaviour. The offset is used exactly as sent - it is never defaulted.
     *
     * @param bond the bond; must not be {@code null}
     * @return the number of days before the payment date, or
     *         {@link Optional#empty()} when the bond has no record-date rule
     */
    private static Optional<Integer> recordDateOffset(Bond bond) {

        Integer days = bond.getRecordDate();

        if (days == null || days <= 0) {
            return Optional.empty();
        }

        return Optional.of(days);
    }

    /**
     * Resolves the day offset for a description.
     *
     * @return the offset, or {@link Optional#empty()} when no record-date rule
     *         applies to this bond
     * @throws UnsupportedRecordDateDescriptionException when the description is
     *                                                   present and meaningful but
     *                                                   not understood by any rule
     */
    private Optional<Integer> resolveOffsetDays(String description) {

        if (RecordDateDescriptions.isNotApplicable(description)) {
            return Optional.empty();
        }

        String normalizedDescription = RecordDateDescriptions.normalize(description);

        for (RecordDateRule rule : rules) {

            Optional<Integer> offsetDays = rule.resolveOffsetDays(normalizedDescription);

            if (offsetDays.isPresent()) {
                return offsetDays;
            }
        }

        throw new UnsupportedRecordDateDescriptionException(description);
    }
}
