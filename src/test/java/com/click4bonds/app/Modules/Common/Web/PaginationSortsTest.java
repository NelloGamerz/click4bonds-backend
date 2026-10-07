package com.click4bonds.app.Modules.Common.Web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * What a paged endpoint will and will not sort by.
 *
 * <p>These describe {@link PaginationSorts} on its own, without a Spring context:
 * which sort reaches the repository, what an unsorted request is given instead,
 * and which requests are refused.</p>
 */
class PaginationSortsTest {

    private static final Set<String> ALLOWED = Set.of("createdAt", "email", "id");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void unsortedRequestGetsTheDefaultSort() {

        Pageable restricted = PaginationSorts.restrict(
                PageRequest.of(0, 20),
                ALLOWED,
                DEFAULT_SORT);

        assertEquals(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")),
                restricted.getSort());
    }

    @Test
    void permittedSortIsKept() {

        Pageable restricted = PaginationSorts.restrict(
                PageRequest.of(2, 50, Sort.by(Sort.Direction.ASC, "email")),
                ALLOWED,
                DEFAULT_SORT);

        assertEquals(
                Sort.by(Sort.Order.asc("email"), Sort.Order.asc("id")),
                restricted.getSort());
    }

    @Test
    void tieBreakerIsAppendedOnlyOnce() {

        Pageable restricted = PaginationSorts.restrict(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")),
                ALLOWED,
                DEFAULT_SORT);

        assertEquals(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
                restricted.getSort());
    }

    @Test
    void pageNumberAndSizeSurvive() {

        Pageable restricted = PaginationSorts.restrict(
                PageRequest.of(3, 7, Sort.by("email")),
                ALLOWED,
                DEFAULT_SORT);

        assertEquals(3, restricted.getPageNumber());
        assertEquals(7, restricted.getPageSize());
    }

    @Test
    void unsortablePropertyIsRefused() {

        BadRequestException thrown = assertThrows(
                BadRequestException.class,
                () -> PaginationSorts.restrict(
                        PageRequest.of(0, 20, Sort.by("passwordHash")),
                        ALLOWED,
                        DEFAULT_SORT));

        assertTrue(
                thrown.getMessage().contains("passwordHash"),
                "the refused property should be named: " + thrown.getMessage());
    }

    @Test
    void everyUnsortablePropertyIsNamed() {

        BadRequestException thrown = assertThrows(
                BadRequestException.class,
                () -> PaginationSorts.restrict(
                        PageRequest.of(0, 20, Sort.by("secret", "token")),
                        ALLOWED,
                        DEFAULT_SORT));

        assertTrue(thrown.getMessage().contains("secret"));
        assertTrue(thrown.getMessage().contains("token"));
    }

    @Test
    void sortableFieldsAreListedWhenRefusing() {

        BadRequestException thrown = assertThrows(
                BadRequestException.class,
                () -> PaginationSorts.restrict(
                        PageRequest.of(0, 20, Sort.by("passwordHash")),
                        ALLOWED,
                        DEFAULT_SORT));

        // Sorted, so the same input always produces the same message.
        assertTrue(
                thrown.getMessage().contains("Sortable fields: createdAt, email, id"),
                thrown.getMessage());
    }

    @Test
    void unsortableAlongsideSortableIsStillRefused() {

        // A caller mixing one good property with one bad one is asking for
        // something the endpoint cannot honour, so the whole request is refused
        // rather than quietly half-applied.
        assertThrows(
                BadRequestException.class,
                () -> PaginationSorts.restrict(
                        PageRequest.of(0, 20, Sort.by("email", "passwordHash")),
                        ALLOWED,
                        DEFAULT_SORT));
    }

    @Test
    void unpagedIsReturnedUnchanged() {

        assertSame(
                Pageable.unpaged(),
                PaginationSorts.restrict(Pageable.unpaged(), ALLOWED, DEFAULT_SORT));
    }
}
