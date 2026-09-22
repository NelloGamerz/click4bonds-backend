package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;

/**
 * The oversell scenario: two buyers, 1000 units, 700 asked for by one and 500 by
 * the other.
 *
 * <pre>
 * remaining = 1000
 * request A = 700
 * request B = 500
 * </pre>
 *
 * <p>Both cannot succeed. Exactly one must, the other must be rejected rather
 * than silently dropped, and the bond must never end up with a negative
 * quantity.</p>
 *
 * <p><strong>What this test does and does not prove.</strong> The real guarantee
 * is the SQL predicate in {@code BondRepository#reserveQuantity}
 * ({@code WHERE remaining_quantity >= :quantity}), which PostgreSQL evaluates
 * while holding the row lock. That statement cannot run here: this project has
 * no embedded database and no Testcontainers, and its only Spring integration
 * tests point at the shared dev database, which a concurrency test must not
 * mutate. The fake below therefore implements that same contract under the same
 * kind of mutual exclusion, and what is asserted is that the writer behaves
 * correctly given the contract — one success, one business error, no negative
 * inventory, one deal written. Exercising the database itself would need
 * Testcontainers, which is not a dependency of this project.</p>
 */
@ExtendWith(MockitoExtension.class)
class DealConfirmationConcurrencyTest {

    private static final String ISIN = "INE123A01016";
    private static final String CLERK_ID = "user_2abc";

    @Mock
    private DealConfirmationRepository dealConfirmationRepository;

    @Mock
    private UserService userService;

    @Mock
    private DealReferenceGenerator dealReferenceGenerator;

    // ============================================================
    // CONCURRENCY
    // ============================================================

    @Test
    void onlyOneOfTwoConcurrentRequestsCanTakeTheInventory() throws Exception {

        AtomicBondInventory inventory = new AtomicBondInventory(1000L);

        DealConfirmationWriter writer = writerFor(inventory.bondRepository());

        stubCustomer();
        stubUniqueReferences();
        stubSave();

        CyclicBarrier startTogether = new CyclicBarrier(2);

        List<Callable<String>> requests = List.of(
                attempt(startTogether, writer, 100L, 7L), // 700 units
                attempt(startTogether, writer, 100L, 5L)); // 500 units

        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Future<String>> futures = pool.invokeAll(requests);

            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }

            long succeeded = outcomes.stream().filter("SUCCESS"::equals).count();
            long rejected = outcomes.stream().filter("REJECTED"::equals).count();

            assertEquals(1, succeeded, "exactly one purchase must go through");
            assertEquals(1, rejected, "the other must be rejected, not silently accepted");

            /*
             * 1000 - 700 = 300 or 1000 - 500 = 500. Whichever request won, the
             * loser took nothing.
             */
            assertTrue(
                    inventory.remaining() == 300L || inventory.remaining() == 500L,
                    "unexpected remaining inventory: " + inventory.remaining());

            assertTrue(inventory.remaining() >= 0, "inventory must never go negative");

            assertEquals(
                    1,
                    inventory.reservationsGranted(),
                    "the database accepted exactly one reservation");

            verify(dealConfirmationRepository).save(any(DealConfirmation.class));

        } finally {
            pool.shutdownNow();
        }
    }

    // ============================================================
    // REJECTION LEAVES NOTHING BEHIND
    // ============================================================

    @Test
    void aRejectedRequestReservesNothingAndWritesNoDeal() {

        AtomicBondInventory inventory = new AtomicBondInventory(1000L);

        DealConfirmationWriter writer = writerFor(inventory.bondRepository());

        stubCustomer();

        assertThrows(
                ConflictException.class,
                () -> writer.create(CLERK_ID, request(100L, 11L), null)); // 1100 units

        assertEquals(1000L, inventory.remaining(), "a rejected request reserves nothing");
        assertEquals(0, inventory.reservationsGranted());

        verify(dealConfirmationRepository, never()).save(any());
    }

    // ============================================================
    // FIXTURES
    // ============================================================

    private DealConfirmationWriter writerFor(BondRepository bondRepository) {

        return new DealConfirmationWriter(
                dealConfirmationRepository,
                bondRepository,
                userService,
                dealReferenceGenerator,
                new DealConfirmationMapper());
    }

    private Callable<String> attempt(
            CyclicBarrier startTogether,
            DealConfirmationWriter writer,
            long quantityPerLot,
            long numberOfLots) {

        return () -> {

            // Line both requests up so they really do overlap.
            startTogether.await(10, TimeUnit.SECONDS);

            try {
                writer.create(
                        CLERK_ID,
                        new CreateDealConfirmationRequest(ISIN, quantityPerLot, numberOfLots),
                        null);

                return "SUCCESS";

            } catch (ConflictException rejected) {
                return "REJECTED";
            }
        };
    }

    private CreateDealConfirmationRequest request(long quantityPerLot, long numberOfLots) {
        return new CreateDealConfirmationRequest(ISIN, quantityPerLot, numberOfLots);
    }

    private void stubCustomer() {

        lenient().when(userService.getUserByClerkId(CLERK_ID))
                .thenReturn(customer());
    }

    /** A distinct reference per deal, so a repeat could not hide behind one. */
    private void stubUniqueReferences() {

        AtomicLong issued = new AtomicLong();

        lenient().when(dealReferenceGenerator.next(any()))
                .thenAnswer(invocation ->
                        "DC-20260922-" + String.format("%06d", issued.incrementAndGet()));
    }

    private void stubSave() {

        lenient().when(dealConfirmationRepository.save(any(DealConfirmation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private User customer() {

        return User.builder()
                .id(UUID.randomUUID())
                .clerkUserId(CLERK_ID)
                .email("customer@example.com")
                .status(UserStatus.ACTIVE)
                .build();
    }

    /**
     * Stand-in for the bond's inventory row.
     *
     * <p>Mirrors the contract of the conditional UPDATE: the check and the
     * decrement happen together, under one lock. Without that, two interleaved
     * buyers could both pass the check and oversell — which is exactly the bug
     * the SQL predicate exists to prevent.</p>
     */
    private static final class AtomicBondInventory {

        private final UUID bondId = UUID.randomUUID();

        private final AtomicLong remaining;

        private int reservationsGranted;

        AtomicBondInventory(long remaining) {
            this.remaining = new AtomicLong(remaining);
        }

        /** Equivalent to {@code UPDATE ... WHERE remaining_quantity >= :quantity}. */
        synchronized int reserve(long quantity) {

            if (remaining.get() < quantity) {
                return 0;
            }

            remaining.addAndGet(-quantity);
            reservationsGranted++;

            return 1;
        }

        long remaining() {
            return remaining.get();
        }

        synchronized int reservationsGranted() {
            return reservationsGranted;
        }

        /**
         * A repository whose reservation is the real thing and whose reads return
         * the current state, so the writer sees what a reload would show it.
         */
        BondRepository bondRepository() {

            BondRepository repository = mock(BondRepository.class);

            lenient().when(repository.findByIsin(ISIN))
                    .thenAnswer(invocation -> Optional.of(currentBond()));

            lenient().when(repository.findById(any()))
                    .thenAnswer(invocation -> Optional.of(currentBond()));

            lenient().when(repository.reserveQuantity(any(), anyLong()))
                    .thenAnswer(invocation -> reserve(invocation.getArgument(1)));

            lenient().when(repository.save(any(Bond.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            return repository;
        }

        private Bond currentBond() {

            return Bond.builder()
                    .id(bondId)
                    .name("Test Bond")
                    .isin(ISIN)
                    .status(BondStatus.ACTIVE)
                    .remainingQuantity(remaining.get())
                    .price(new BigDecimal("98.94"))
                    .build();
        }
    }
}
