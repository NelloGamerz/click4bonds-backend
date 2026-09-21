package com.click4bonds.app.Modules.Bond.Service;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves record dates expressed as a whole number of days relative to a
 * coupon / interest payment date.
 *
 * <p>
 * This is the shape used by most Indian listed corporate bond documentation and
 * is the only strategy implemented today. The number of days is always read
 * from the description - it is never assumed to be 15, 7 or any other value.
 *
 * <p>
 * Supported wording (all case-insensitive, punctuation and spacing tolerant):
 *
 * <pre>
 * 15 days prior to interest payment date
 * 15 days before payment date
 * 15 days prior
 * 7 days prior to interest payment
 * 10 days before coupon date
 * 15-Days prior to interest payment date.
 * Record date shall be 15 days prior to interest payment date
 * 15 days prior to each interest payment date
 * 2 days before coupon
 * 5 days after payment date      (offset after the payment date)
 * </pre>
 *
 * <p>
 * A description that clearly carries a day offset but adds meaning this rule
 * cannot interpret (for example "3 days prior to the last working day") yields
 * {@link Optional#empty()} rather than a guess, so the parser fails loudly.
 */
public class RelativeDaysRecordDateRule implements RecordDateRule {

    /**
     * Captures the day offset and its direction.
     *
     * <p>
     * The trailing reference ("interest payment date", "coupon", ...) is
     * validated separately against {@link #REFERENCE_TOKENS} rather than being
     * baked into this pattern. That keeps the pattern readable and lets the
     * reference be described in any order with any of the usual synonyms.
     *
     * <p>
     * Group 1 = number of days.
     * Group 2 = direction keyword.
     */
    private static final Pattern OFFSET_PATTERN = Pattern.compile(
            "(\\d{1,4})\\s*DAYS?\\s*"
                    + "(PRIOR|PREVIOUS|BEFORE|EARLIER|AFTER|AHEAD)");

    /**
     * Word order is not fixed in practice, so the connector between the
     * direction and its reference is stripped instead of being required:
     *
     * <pre>
     * 15 days prior to interest payment date
     * 15 days earlier than the coupon date
     * </pre>
     */
    private static final Pattern DIRECTION_CONNECTOR = Pattern.compile(
            "^\\s*(?:TO|THAN|OF|ON)\\s+");

    /**
     * A range is inherently ambiguous - taking either bound would be a guess -
     * so "15-20 days prior" must fail instead of silently becoming 20 days.
     *
     * <p>
     * Matched against the normalized description, where "15-20" has already
     * become "15 20".
     */
    private static final Pattern RANGE_PATTERN = Pattern.compile(
            "\\b\\d{1,4}\\s+(?:TO\\s+)?\\d{1,4}\\s+DAYS?\\b");

    /**
     * Words that may appear after the direction without changing the meaning:
     * they can only refer to the coupon / interest payment date itself, which
     * is the date the offset is applied to.
     *
     * <p>
     * Anything left over after these tokens are removed is meaning this rule
     * does not understand, and therefore a reason to refuse rather than guess.
     */
    private static final Pattern REFERENCE_TOKENS = Pattern.compile(
            "\\b(?:THE|EACH|EVERY|ANY|INTEREST|COUPON|IP|PAYMENT|PAYOUT|PAY|OUT"
                    + "|DUE|DATE|DATES|OF|TO|THAN|ON|A|AN)\\b");

    /**
     * Hours, working days and other qualifiers are deliberately absent from
     * {@link #REFERENCE_TOKENS}: they change the arithmetic and are not
     * supported, so they must not be silently discarded.
     */

    @Override
    public Optional<Integer> resolveOffsetDays(String normalizedDescription) {

        if (normalizedDescription == null || normalizedDescription.isBlank()) {
            return Optional.empty();
        }

        if (RANGE_PATTERN.matcher(normalizedDescription).find()) {
            return Optional.empty();
        }

        Matcher offsetMatcher = OFFSET_PATTERN.matcher(normalizedDescription);

        if (!offsetMatcher.find()) {
            return Optional.empty();
        }

        /*
         * Several different offsets in one description cannot be reduced to a
         * single record date, so refuse rather than pick one.
         */
        if (hasConflictingOffsets(normalizedDescription, offsetMatcher)) {
            return Optional.empty();
        }

        int days = Integer.parseInt(offsetMatcher.group(1));

        if (days <= 0) {
            return Optional.empty();
        }

        String direction = offsetMatcher.group(2);

        if (!isReferenceUnderstood(normalizedDescription, offsetMatcher.end())) {
            return Optional.empty();
        }

        return Optional.of(isForwardDirection(direction) ? -days : days);
    }

    /**
     * Detects a second, different day/direction pair in the same description.
     *
     * <p>
     * Example of what must be rejected:
     *
     * <pre>
     * 15 days prior to interest payment date and 7 days prior to maturity
     * </pre>
     */
    private boolean hasConflictingOffsets(
            String normalizedDescription,
            Matcher firstMatch) {

        Set<String> offsets = new LinkedHashSet<>();

        offsets.add(firstMatch.group(1) + " " + firstMatch.group(2));

        Matcher matcher = OFFSET_PATTERN.matcher(normalizedDescription);

        while (matcher.find()) {
            offsets.add(matcher.group(1) + " " + matcher.group(2));
        }

        return offsets.size() > 1;
    }

    /**
     * Verifies that everything after the offset only refers to the payment
     * date.
     *
     * <p>
     * The known reference words are removed; if anything is left, the
     * description carries meaning this rule does not implement and must not be
     * approximated.
     *
     * @param normalizedDescription the full normalized description
     * @param offsetEndIndex        index just past the matched direction keyword
     */
    private boolean isReferenceUnderstood(
            String normalizedDescription,
            int offsetEndIndex) {

        String reference = normalizedDescription.substring(offsetEndIndex);

        reference = DIRECTION_CONNECTOR.matcher(reference).replaceFirst("");

        String leftovers = REFERENCE_TOKENS.matcher(reference)
                .replaceAll(" ")
                /*
                 * Digits are removed as well: a second number left over here
                 * (for example "15 days prior to the 21st") is meaning we have
                 * not modelled.
                 */
                .replaceAll("[^A-Z]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return leftovers.isEmpty();
    }

    /**
     * @return {@code true} for directions that place the record date after the
     *         payment date
     */
    private boolean isForwardDirection(String direction) {
        return "AFTER".equals(direction) || "AHEAD".equals(direction);
    }
}
