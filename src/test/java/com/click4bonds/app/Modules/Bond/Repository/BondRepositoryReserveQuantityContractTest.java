package com.click4bonds.app.Modules.Bond.Repository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Protects the atomic oversell guard that deal confirmation no longer uses.
 *
 * <p>Removing the reservation from the deal flow left
 * {@link BondRepository#reserveQuantity} with no caller in production code. It
 * is kept deliberately: it is the actual oversell protection, and the
 * reservation step is expected to call it before a draft deal exists. A method
 * with no callers is exactly the kind that gets "cleaned up" or "simplified", so
 * its two load-bearing properties are pinned here.</p>
 *
 * <p><strong>Why this is reflective.</strong> The guarantee is what PostgreSQL
 * does with the statement, and this project has no embedded database or
 * Testcontainers — its only Spring integration tests point at the shared dev
 * database, which a concurrency test must not mutate. What can be asserted
 * without one is the shape of the statement: that it stays a single conditional
 * UPDATE whose guard is evaluated by the database rather than a read-then-write
 * in Java. A rewrite into SELECT / check / UPDATE would fail these assertions
 * even though it might pass a functional test on a quiet database.</p>
 */
class BondRepositoryReserveQuantityContractTest {

    // ============================================================
    // THE OPERATION STILL EXISTS
    // ============================================================

    @Test
    void reserveQuantityIsStillDeclaredAndReturnsAnAffectedRowCount() throws Exception {

        Method reserve = reserveQuantityMethod();

        /*
         * The int return is part of the contract: callers distinguish
         * "reserved" from "not enough units" by the row count, never by reading
         * the bond back.
         */
        assertEquals(int.class, reserve.getReturnType());
        assertEquals(2, reserve.getParameterCount(),
                "reserveQuantity takes exactly the bond id and the quantity");
    }

    @Test
    void reserveQuantityIsAWriteAndIsParameterised() throws Exception {

        Method reserve = reserveQuantityMethod();

        assertNotNull(
                reserve.getAnnotation(Modifying.class),
                "a mutating repository method must be @Modifying");

        // The guard is only safe if both values are bound, not interpolated.
        List<String> params = Arrays.stream(reserve.getParameters())
                .map(parameter -> parameter.getAnnotation(Param.class))
                .filter(Objects::nonNull)
                .map(Param::value)
                .toList();

        assertTrue(params.contains("id"), "the bond id must be bound as :id");
        assertTrue(params.contains("quantity"), "the quantity must be bound as :quantity");
    }

    // ============================================================
    // THE STATEMENT IS STILL ATOMIC
    // ============================================================

    @Test
    void reserveQuantityRemainsASingleConditionalUpdate() throws Exception {

        String jpql = normalizedQuery();

        /*
         * One statement, one row-locked predicate. Any SELECT here would mean
         * the check had moved into Java, which is the race this exists to
         * prevent.
         */
        assertTrue(
                jpql.startsWith("UPDATE Bond b"),
                "reserveQuantity must be a single UPDATE, was: " + jpql);
        assertTrue(
                !jpql.toUpperCase().contains("SELECT"),
                "reserveQuantity must not read the row first, was: " + jpql);
    }

    @Test
    void reserveQuantityKeepsTheOversellPredicate() throws Exception {

        String jpql = normalizedQuery();

        /*
         * THE guard. Evaluated by the database while it holds the row lock, so
         * two concurrent reservations serialize and the loser matches zero rows
         * instead of taking the inventory negative.
         */
        assertTrue(
                jpql.contains("b.remainingQuantity >= :quantity"),
                "the atomic oversell predicate must not be removed, was: " + jpql);

        // And the decrement it guards.
        assertTrue(
                jpql.contains("SET b.remainingQuantity = b.remainingQuantity - :quantity"),
                "the guarded decrement must remain a relative update, was: " + jpql);
    }

    @Test
    void reserveQuantityIsTheOnlyInventoryMutationOnTheRepository() {

        /*
         * Deal confirmation is inventory-neutral, and this is the other half of
         * that claim: there is no second, unguarded statement on the bond
         * repository that a future caller could reach for instead.
         */
        List<String> mutating = Arrays.stream(BondRepository.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Modifying.class) != null)
                .map(Method::getName)
                .toList();

        assertEquals(
                List.of("reserveQuantity"),
                mutating,
                "reserveQuantity must remain the only inventory-mutating statement");
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Method reserveQuantityMethod() throws Exception {
        return BondRepository.class.getMethod("reserveQuantity", UUID.class, Long.class);
    }

    /** The JPQL with its formatting collapsed, so assertions read as one line. */
    private String normalizedQuery() throws Exception {

        Query query = reserveQuantityMethod().getAnnotation(Query.class);

        assertNotNull(query, "reserveQuantity must declare its JPQL explicitly");

        return query.value().replaceAll("\\s+", " ").trim();
    }
}
