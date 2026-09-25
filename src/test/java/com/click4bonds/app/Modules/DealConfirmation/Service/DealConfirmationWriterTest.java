package com.click4bonds.app.Modules.DealConfirmation.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Bond.Enums.BondStatus;
import com.click4bonds.app.Modules.Bond.Models.Bond;
import com.click4bonds.app.Modules.Bond.Repository.BondRepository;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;

/**
 * Behaviour of the deal creation transaction.
 *
 * <p>The inventory guard itself is asserted at two levels: that the write goes
 * through the single conditional UPDATE (never a read-modify-write on the loaded
 * bond), and that a zero row count is turned into a business error rather than a
 * deal. {@code DealConfirmationConcurrencyTest} covers what happens when two
 * requests race.</p>
 */
@ExtendWith(MockitoExtension.class)
class DealConfirmationWriterTest {

    private static final String ISIN = "INE123A01016";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private DealConfirmationRepository dealConfirmationRepository;

    @Mock
    private BondRepository bondRepository;

    @Mock
    private UserService userService;

    @Mock
    private DealReferenceGenerator dealReferenceGenerator;

    private DealConfirmationWriter writer;

    @BeforeEach
    void setUp() {
        writer = new DealConfirmationWriter(
                dealConfirmationRepository,
                bondRepository,
                userService,
                dealReferenceGenerator,
                new DealConfirmationMapper());
    }

    // ============================================================
    // SUCCESS
    // ============================================================

    @Test
    void createsDealAndReservesInventory() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        givenActiveCustomer();
        givenBond(bond);
        givenReservationSucceeds(bond, 500L);
        givenReference("DC-20260922-000001");
        givenSaveReturnsItsArgument();

        DealConfirmationWriter.CreatedDeal created =
                writer.create(USER_ID, request(100L, 5L), null);

        assertEquals("DC-20260922-000001", created.response().getDealReference());
        assertEquals(ISIN, created.response().getIsin());
        assertEquals(100L, created.response().getQuantityPerLot());
        assertEquals(5L, created.response().getNumberOfLots());
        assertEquals(500L, created.response().getTotalQuantity());

        // The quantity reserved is the server-calculated total, not a client value.
        Mockito.verify(bondRepository).reserveQuantity(bond.getId(), 500L);

        // 500 units at a price of 98.94 each.
        assertEquals(
                new BigDecimal("49470.00"),
                created.response().getTotalAmount());

        // The snapshot the future Excel/PDF step consumes is filled in.
        assertEquals("DC-20260922-000001", created.documentData().dealReference());
        assertEquals("Test Bond", created.documentData().bondName());
        assertEquals(500L, created.documentData().totalQuantity());
    }

    @Test
    void storesIdempotencyKeyOnTheDeal() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        givenActiveCustomer();
        givenBond(bond);
        givenReservationSucceeds(bond, 500L);
        givenReference("DC-20260922-000001");
        givenSaveReturnsItsArgument();

        writer.create(USER_ID, request(100L, 5L), "key-1");

        Mockito.verify(dealConfirmationRepository).save(
                Mockito.argThat(deal -> "key-1".equals(deal.getIdempotencyKey())));
    }

    @Test
    void takesTheLastUnitsAndMarksTheBondSoldOut() {

        Bond bond = bond(BondStatus.ACTIVE, 500L);

        givenActiveCustomer();
        givenBond(bond);
        Bond reserved = givenReservationSucceeds(bond, 0L);
        givenReference("DC-20260922-000002");
        givenSaveReturnsItsArgument();

        writer.create(USER_ID, request(100L, 5L), null);

        assertEquals(0L, reserved.getRemainingQuantity());
        assertEquals(BondStatus.SOLD_OUT, reserved.getStatus());

        Mockito.verify(bondRepository).save(reserved);
    }

    @Test
    void leavesTheBondStatusAloneWhileUnitsRemain() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        givenActiveCustomer();
        givenBond(bond);
        givenReservationSucceeds(bond, 500L);
        givenReference("DC-20260922-000003");
        givenSaveReturnsItsArgument();

        writer.create(USER_ID, request(100L, 5L), null);

        assertEquals(BondStatus.ACTIVE, bond.getStatus());

        Mockito.verify(bondRepository, Mockito.never()).save(any(Bond.class));
    }

    @Test
    void recordsNoAmountWhenTheBondHasNoPrice() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);
        bond.setPrice(null);

        givenActiveCustomer();
        givenBond(bond);
        givenReservationSucceeds(bond, 500L);
        givenReference("DC-20260922-000004");
        givenSaveReturnsItsArgument();

        DealConfirmationWriter.CreatedDeal created =
                writer.create(USER_ID, request(100L, 5L), null);

        /*
         * An unknown price must not be recorded as a free purchase.
         */
        assertNull(created.response().getPricePerUnit());
        assertNull(created.response().getTotalAmount());
    }

    @Test
    void normalisesIsinBeforeLookup() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        givenActiveCustomer();
        givenBond(bond);
        givenReservationSucceeds(bond, 500L);
        givenReference("DC-20260922-000005");
        givenSaveReturnsItsArgument();

        writer.create(
                USER_ID,
                new CreateDealConfirmationRequest("ine123a01016", 100L, 5L),
                null);

        Mockito.verify(bondRepository).findByIsin(ISIN);
    }

    // ============================================================
    // INVALID REQUEST
    // ============================================================

    @Test
    void rejectsTotalQuantityThatOverflows() {

        assertThrows(
                BadRequestException.class,
                () -> writer.create(
                        USER_ID,
                        request(Long.MAX_VALUE, 2L),
                        null));

        // Nothing was read, reserved or written.
        Mockito.verifyNoInteractions(bondRepository);
        Mockito.verifyNoInteractions(dealConfirmationRepository);
    }

    // ============================================================
    // BOND LOOKUP AND AVAILABILITY
    // ============================================================

    @Test
    void rejectsUnknownIsinWithoutTouchingInventory() {

        givenActiveCustomer();
        Mockito.when(bondRepository.findByIsin(ISIN))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> writer.create(USER_ID, request(100L, 5L), null));

        Mockito.verify(bondRepository, Mockito.never())
                .reserveQuantity(any(), anyLong());
        Mockito.verify(dealConfirmationRepository, Mockito.never())
                .save(any());
    }

    @Test
    void rejectsBondThatIsNotActive() {

        Bond bond = bond(BondStatus.SUSPENDED, 1000L);

        givenActiveCustomer();
        givenBond(bond);

        assertThrows(
                BadRequestException.class,
                () -> writer.create(USER_ID, request(100L, 5L), null));

        Mockito.verify(bondRepository, Mockito.never())
                .reserveQuantity(any(), anyLong());
        Mockito.verify(dealConfirmationRepository, Mockito.never())
                .save(any());
    }

    @Test
    void rejectsBondWhoseInventoryWasNeverConfigured() {

        Bond bond = bond(BondStatus.ACTIVE, null);

        givenActiveCustomer();
        givenBond(bond);

        assertThrows(
                BadRequestException.class,
                () -> writer.create(USER_ID, request(100L, 5L), null));

        Mockito.verify(bondRepository, Mockito.never())
                .reserveQuantity(any(), anyLong());
    }

    @Test
    void rejectsBondThatIsSoldOut() {

        Bond bond = bond(BondStatus.SOLD_OUT, 0L);

        givenActiveCustomer();
        givenBond(bond);

        assertThrows(
                BadRequestException.class,
                () -> writer.create(USER_ID, request(100L, 5L), null));

        Mockito.verify(bondRepository, Mockito.never())
                .reserveQuantity(any(), anyLong());
        Mockito.verify(dealConfirmationRepository, Mockito.never())
                .save(any());
    }

    // ============================================================
    // INVENTORY
    // ============================================================

    @Test
    void rejectsRequestLargerThanRemainingInventory() {

        Bond bond = bond(BondStatus.ACTIVE, 400L);

        givenActiveCustomer();
        givenBond(bond);

        assertThrows(
                ConflictException.class,
                () -> writer.create(USER_ID, request(100L, 5L), null));

        // The row was never even asked to give up units.
        Mockito.verify(bondRepository, Mockito.never())
                .reserveQuantity(any(), anyLong());
        Mockito.verify(dealConfirmationRepository, Mockito.never())
                .save(any());
    }

    @Test
    void rejectsRequestWhenTheAtomicReservationMatchedNoRow() {

        /*
         * The bond looked fine when read, but another buyer took the units before
         * this request's UPDATE ran — so the conditional UPDATE matched no row.
         * This is the case only the database can detect, and it must not produce
         * a deal.
         */
        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        givenActiveCustomer();
        givenBond(bond);
        Mockito.when(bondRepository.reserveQuantity(bond.getId(), 500L))
                .thenReturn(0);

        assertThrows(
                ConflictException.class,
                () -> writer.create(USER_ID, request(100L, 5L), null));

        Mockito.verify(dealConfirmationRepository, Mockito.never())
                .save(any());
    }

    @Test
    void reservesInventoryBeforeWritingTheDeal() {

        /*
         * Ordering is the whole safety argument: if the deal insert fails the
         * transaction rolls back and the units come back, whereas inserting the
         * deal first could leave a deal with no units behind it.
         */
        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        givenActiveCustomer();
        givenBond(bond);
        givenReservationSucceeds(bond, 500L);
        givenReference("DC-20260922-000006");
        givenSaveReturnsItsArgument();

        writer.create(USER_ID, request(100L, 5L), null);

        InOrder order = Mockito.inOrder(bondRepository, dealConfirmationRepository);

        order.verify(bondRepository).reserveQuantity(bond.getId(), 500L);
        order.verify(dealConfirmationRepository).save(any());
    }

    @Test
    void propagatesFailureToWriteTheDealSoTheReservationRollsBack() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        givenActiveCustomer();
        givenBond(bond);
        givenReservationSucceeds(bond, 500L);
        givenReference("DC-20260922-000007");

        Mockito.when(dealConfirmationRepository.save(any()))
                .thenThrow(new IllegalStateException("insert failed"));

        assertThrows(
                IllegalStateException.class,
                () -> writer.create(USER_ID, request(100L, 5L), null));

        /*
         * The exception escapes the @Transactional boundary, which is what makes
         * the reservation roll back with it. That rollback itself is Spring's and
         * the database's; with no embedded database in this project it is not
         * asserted here.
         */
        Mockito.verify(bondRepository).reserveQuantity(bond.getId(), 500L);
    }

    // ============================================================
    // CUSTOMER
    // ============================================================

    @Test
    void rejectsInactiveCustomer() {

        User suspended = customer();
        suspended.setStatus(UserStatus.SUSPENDED);

        Mockito.when(userService.getUser(USER_ID))
                .thenReturn(suspended);

        assertThrows(
                ForbiddenException.class,
                () -> writer.create(USER_ID, request(100L, 5L), null));

        Mockito.verifyNoInteractions(bondRepository);
    }

    // ============================================================
    // FIXTURES
    // ============================================================

    private CreateDealConfirmationRequest request(long quantityPerLot, long numberOfLots) {
        return new CreateDealConfirmationRequest(ISIN, quantityPerLot, numberOfLots);
    }

    private Bond bond(BondStatus status, Long remainingQuantity) {

        return Bond.builder()
                .id(UUID.randomUUID())
                .name("Test Bond")
                .isin(ISIN)
                .status(status)
                .remainingQuantity(remainingQuantity)
                .price(new BigDecimal("98.94"))
                .build();
    }

    private User customer() {

        return User.builder()
                .id(USER_ID)
                .email("customer@example.com")
                .firstName("Test")
                .lastName("Customer")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private void givenActiveCustomer() {
        Mockito.when(userService.getUser(USER_ID))
                .thenReturn(customer());
    }

    private void givenBond(Bond bond) {
        Mockito.when(bondRepository.findByIsin(ISIN)).thenReturn(Optional.of(bond));
    }

    /**
     * Stubs the reservation the way the real conditional UPDATE behaves: it
     * succeeds, and the bond read back afterwards carries the new figure.
     *
     * @return the bond the writer will see after reserving — the one it mutates
     */
    private Bond givenReservationSucceeds(Bond bond, long remainingAfter) {

        Mockito.when(bondRepository.reserveQuantity(bond.getId(), 500L))
                .thenReturn(1);

        Bond afterReservation = bond(bond.getStatus(), remainingAfter);
        afterReservation.setId(bond.getId());
        afterReservation.setPrice(bond.getPrice());

        Mockito.when(bondRepository.findById(bond.getId()))
                .thenReturn(Optional.of(afterReservation));

        return afterReservation;
    }

    private void givenReference(String reference) {
        Mockito.when(dealReferenceGenerator.next(any(LocalDate.class)))
                .thenReturn(reference);
    }

    private void givenSaveReturnsItsArgument() {
        Mockito.when(dealConfirmationRepository.save(any(DealConfirmation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
