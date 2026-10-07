package com.click4bonds.app.Modules.Common.Web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * Constrains what a caller of a paged endpoint is allowed to sort by.
 *
 * <p>Spring Data binds {@code ?sort=} to any property name the caller supplies,
 * with no knowledge of which properties the endpoint actually offers. Two things
 * go wrong when that is left alone.</p>
 *
 * <p>The first is a plan the caller did not pay for. Sorting by a column with no
 * index turns each page request into a sort of the entire matching set, and the
 * set is chosen by the caller — so an unrelated, unindexed property can be used
 * to make an endpoint arbitrarily expensive.</p>
 *
 * <p>The second is pagination that silently repeats or skips rows. An endpoint
 * whose pageable carries no sort at all has no defined row order: PostgreSQL is
 * free to return a different order for the same query as the plan or the physical
 * layout changes, so page 2 can contain a row page 1 already showed. Every sort
 * this class returns therefore ends with {@value #TIE_BREAKER} appended, which
 * gives the order a total, stable definition whenever the requested ordering
 * alone leaves rows tied.</p>
 *
 * <p>Not a bean. There is no state to hold and no configuration to read, and an
 * endpoint's sortable properties are a fact about that endpoint, so they are
 * passed in at the call site rather than registered anywhere central.</p>
 */
public final class PaginationSorts {

    /**
     * Property every returned sort ends with, so that rows the requested sort
     * cannot separate still come back in a fixed order. {@code id} is the
     * primary key and is therefore unique, which is what makes the order total.
     */
    private static final String TIE_BREAKER = "id";

    private PaginationSorts() {
    }

    /**
     * Returns a pageable carrying only a sort that is safe to run.
     *
     * <p>An unsorted request is given {@code defaultSort}; a request naming only
     * permitted properties keeps them. Either way {@value #TIE_BREAKER} is
     * appended unless the caller already asked for it. A request naming a
     * property that is not permitted is refused rather than quietly rewritten —
     * silently dropping the sort would leave a caller who asked for a particular
     * ordering looking at a different one with no indication why.</p>
     *
     * @param pageable           the bound request, whose page number and size are
     *                           kept as they are
     * @param sortableProperties the property names this endpoint permits, in the
     *                           entity's own spelling
     * @param defaultSort        the order to apply when the caller asked for
     *                           none; should itself be a sensible default order
     *                           for the endpoint, not merely any sort
     * @return an equivalent pageable whose sort is one of the two above
     * @throws BadRequestException if the caller named a property outside
     *                             {@code sortableProperties}
     */
    public static Pageable restrict(
            Pageable pageable,
            Set<String> sortableProperties,
            Sort defaultSort) {

        // Unpaged has no ordering to fix and no page to rebuild; leave it be.
        if (pageable.isUnpaged()) {
            return pageable;
        }

        Sort requested = pageable.getSort();

        if (requested.isUnsorted()) {
            return page(
                    pageable,
                    withTieBreaker(defaultSort));
        }

        List<String> refused = new ArrayList<>();

        for (Sort.Order order : requested) {
            if (!sortableProperties.contains(order.getProperty())) {
                refused.add(order.getProperty());
            }
        }

        if (!refused.isEmpty()) {
            // Sorted so the message is the same for the same input, which makes
            // it worth asserting on from a test.
            throw new BadRequestException(
                    "Cannot sort by " + String.join(", ", refused)
                            + ". Sortable fields: "
                            + String.join(", ", new TreeSet<>(sortableProperties)));
        }

        return page(
                pageable,
                withTieBreaker(requested));
    }

    private static Pageable page(Pageable pageable, Sort sort) {

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort);
    }

    private static Sort withTieBreaker(Sort sort) {

        if (sort.getOrderFor(TIE_BREAKER) != null) {
            return sort;
        }

        return sort.and(Sort.by(Sort.Direction.ASC, TIE_BREAKER));
    }
}
