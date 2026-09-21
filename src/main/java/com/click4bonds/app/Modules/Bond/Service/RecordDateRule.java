package com.click4bonds.app.Modules.Bond.Service;

import java.util.Optional;

/**
 * One interpretation strategy for a record-date description.
 *
 * <p>
 * Record-date wording is not standardized in Indian bond documentation, so the
 * parser is built as an ordered list of these strategies rather than a single
 * branching method. Adding support for a new wording (for example
 * "21st of every month", or "5 working days before the coupon date") means
 * adding a rule, not editing the parser or anything downstream.
 *
 * <p>
 * Return {@link Optional#empty()} whenever the rule cannot fully and
 * confidently interpret the description. The parser treats "no rule resolved"
 * as a hard failure, so a rule must never return a best guess.
 */
public interface RecordDateRule {

    /**
     * Resolves the record-date offset for a description that this rule
     * recognizes.
     *
     * @param normalizedDescription the description after
     *                              {@link RecordDateDescriptions#normalize(String)};
     *                              upper-cased, punctuation-free, single-spaced
     * @return the number of days the record date falls <em>before</em> the
     *         payment date, or {@link Optional#empty()} when this rule does not
     *         apply or cannot fully interpret the description. The value may be
     *         negative to express an offset <em>after</em> the payment date.
     */
    Optional<Integer> resolveOffsetDays(String normalizedDescription);
}
