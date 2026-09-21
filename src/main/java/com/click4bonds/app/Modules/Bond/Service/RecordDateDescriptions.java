package com.click4bonds.app.Modules.Bond.Service;

import java.util.Locale;
import java.util.Set;

/**
 * Shared normalization helpers for record-date descriptions.
 *
 * <p>
 * Kept package-private and free of state so that every {@link RecordDateRule}
 * interprets descriptions exactly the same way. Without this, each rule would
 * grow its own slightly different idea of what "the same" description means.
 *
 * <p>
 * The normalization is deliberately aggressive because record-date text arrives
 * from spreadsheets written by hand. All of the following must be understood as
 * the same rule:
 *
 * <pre>
 * 15 days prior to interest payment date
 * 15 DAYS PRIOR TO INTEREST PAYMENT DATE
 * 15-Days prior to interest payment date.
 * 15  days   prior  to  interest  payment  date
 * </pre>
 */
final class RecordDateDescriptions {

    /**
     * Values that mean "this bond has no record-date rule".
     *
     * <p>
     * Compared after {@link #compact(String)}, so {@code "N/A"}, {@code "N.A."}
     * and {@code "na"} all collapse to {@code "NA"}.
     *
     * <p>
     * The empty marker covers spreadsheet placeholders that carry no letters or
     * digits once punctuation is removed, most commonly a lone {@code "-"} or
     * {@code "--"} used to mean "not applicable".
     */
    private static final Set<String> NOT_APPLICABLE_MARKERS = Set.of(
            "",
            "NA",
            "NOTAPPLICABLE",
            "NOTRELEVANT",
            "NIL",
            "NONE",
            "NO"
    );

    private RecordDateDescriptions() {
    }

    /**
     * Normalizes a description for pattern matching.
     *
     * <p>
     * Applies, in order:
     *
     * <ol>
     * <li>upper-casing with {@link Locale#ENGLISH} so results do not depend on
     * the JVM default locale (the same reasoning used by
     * {@code MaturityDescriptionParserImpl} when parsing month names);</li>
     * <li>hyphens joining digits to words or words to words become spaces, so
     * {@code "15-Days"} and {@code "interest-payment"} read as two tokens;</li>
     * <li>remaining punctuation becomes a space, so a trailing period or a
     * parenthesis cannot break a match;</li>
     * <li>whitespace is collapsed and trimmed.</li>
     * </ol>
     *
     * @param description the raw description; may be {@code null}
     * @return the normalized description, or an empty string when {@code null}
     */
    static String normalize(String description) {
        if (description == null) {
            return "";
        }

        return description
                .toUpperCase(Locale.ENGLISH)
                /*
                 * "15-DAYS" -> "15 DAYS"
                 * "interest-payment" -> "interest payment"
                 *
                 * Only hyphens with a letter or digit on both sides are treated
                 * as separators. A standalone "-" placeholder is left to the
                 * punctuation step below.
                 */
                .replaceAll("(?<=[A-Z0-9])\\s*-\\s*(?=[A-Z0-9])", " ")
                /*
                 * Any other punctuation ("." , "/" , "(" , ...) becomes a space.
                 * "/" is preserved by isNotApplicable() through compact().
                 */
                .replaceAll("[^A-Z0-9/]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Reduces a description to letters and digits only.
     *
     * <p>
     * Used only to recognize "not applicable" markers, where the source data
     * varies between {@code NA}, {@code N/A}, {@code N.A.} and {@code N / A}.
     */
    static String compact(String description) {
        return normalize(description).replaceAll("[^A-Z0-9]", "");
    }

    /**
     * @param description the raw description; may be {@code null}
     * @return {@code true} when the description is absent or explicitly states
     *         that no record date applies
     */
    static boolean isNotApplicable(String description) {
        if (description == null || description.isBlank()) {
            return true;
        }

        return NOT_APPLICABLE_MARKERS.contains(compact(description));
    }
}
