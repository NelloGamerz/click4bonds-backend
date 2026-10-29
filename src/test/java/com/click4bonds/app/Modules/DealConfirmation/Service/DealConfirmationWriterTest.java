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
import com.click4bonds.app.Modules.Bond.Service.BondService;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.ForbiddenException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CounterpartyDetails;
import com.click4bonds.app.Modules.DealConfirmation.Dto.CreateDealConfirmationRequest;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.Party.Model.BankAccount;
import com.click4bonds.app.Modules.Party.Model.DematAccount;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Repository.BankAccountRepository;
import com.click4bonds.app.Modules.Party.Repository.DematAccountRepository;
import com.click4bonds.app.Modules.Party.Service.PanDetailsService;
import com.click4bonds.app.Modules.Party.Service.SecretCipher;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealSide;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DeliveryMode;
import com.click4bonds.app.Modules.User.Enums.UserStatus;
import com.click4bonds.app.Modules.User.Model.Client;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Repository.ClientRepository;
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
    private BondService bondService;

    @Mock
    private UserService userService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private DealReferenceGenerator dealReferenceGenerator;

    /*
     * Mocked rather than real: the real calculator needs a bond with a coupon
     * rate and an interest-payment description, and these fixtures are about
     * inventory. An unstubbed Mockito method returning Optional yields an empty
     * Optional, which is exactly the "no accrual" path the writer tolerates.
     */
    @Mock
    private DealAccrualCalculator accrualCalculator;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private DematAccountRepository dematAccountRepository;

    @Mock
    private PanDetailsService panDetailsService;

    @Mock
    private SecretCipher secretCipher;

    private DealConfirmationWriter writer;

    @BeforeEach
    void setUp() {
        writer = new DealConfirmationWriter(
                dealConfirmationRepository,
                bondService,
                userService,
                clientRepository,
                dealReferenceGenerator,
                new DealConfirmationMapper(),
                accrualCalculator,
                new DocumentProperties(),
                bankAccountRepository,
                dematAccountRepository,
                panDetailsService,
                secretCipher);
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
                writer.create(USER_ID, request(5L), null);

        assertEquals("DC-20260922-000001", created.response().getDealReference());
        assertEquals(ISIN, created.response().getIsin());
        assertEquals(100L, created.response().getQuantityPerLot());
        assertEquals(5L, created.response().getNumberOfLots());
        assertEquals(500L, created.response().getTotalQuantity());

        // The quantity reserved is the server-calculated total, not a client value.
        Mockito.verify(bondService).reserveQuantity(bond.getId(), BigDecimal.valueOf(500));

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

        writer.create(USER_ID, request(5L), "key-1");

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

        writer.create(USER_ID, request(5L), null);

        assertEquals(0, BigDecimal.ZERO.compareTo(reserved.getQuantum()));
        assertEquals(BondStatus.SOLD_OUT, reserved.getStatus());

        Mockito.verify(bondService).saveBond(reserved);
    }

    @Test
    void leavesTheBondStatusAloneWhileUnitsRemain() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        givenActiveCustomer();
        givenBond(bond);
        givenReservationSucceeds(bond, 500L);
        givenReference("DC-20260922-000003");
        givenSaveReturnsItsArgument();

        writer.create(USER_ID, request(5L), null);

        assertEquals(BondStatus.ACTIVE, bond.getStatus());

        Mockito.verify(bondService, Mockito.never()).saveBond(any(Bond.class));
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
                writer.create(USER_ID, request(5L), null);

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
                new CreateDealConfirmationRequest("ine123a01016", 5L, null, null),
                null);

        Mockito.verify(bondService).findBond(ISIN);
    }

    // ============================================================
    // INVALID REQUEST
    // ============================================================

    @Test
    void rejectsTotalQuantityThatOverflows() {

        /*
         * An absurd lot size times an absurd number of lots. Both factors are
         * positive, so this is an absurd order rather than a malformed one, and
         * it must not wrap around into a small number and reserve the wrong
         * quantity.
         */
        Bond bond = bond(BondStatus.ACTIVE, 1000L);
        bond.setLotSize(BigDecimal.valueOf(Long.MAX_VALUE));

        givenActiveCustomer();
        givenBond(bond);

        assertThrows(
                BadRequestException.class,
                () -> writer.create(USER_ID, request(2L), null));

        // The bond is read to learn its lot size, but nothing was reserved or
        // written.
        Mockito.verify(bondService, Mockito.never())
                .reserveQuantity(any(), any());
        Mockito.verifyNoInteractions(dealConfirmationRepository);
    }

    // ============================================================
    // BOND LOOKUP AND AVAILABILITY
    // ============================================================

    @Test
    void rejectsUnknownIsinWithoutTouchingInventory() {

        givenActiveCustomer();
        Mockito.when(bondService.findBond(ISIN))
                .thenThrow(new ResourceNotFoundException("Bond not found with ISIN: " + ISIN));

        assertThrows(
                ResourceNotFoundException.class,
                () -> writer.create(USER_ID, request(5L), null));

        Mockito.verify(bondService, Mockito.never())
                .reserveQuantity(any(), any());
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
                () -> writer.create(USER_ID, request(5L), null));

        Mockito.verify(bondService, Mockito.never())
                .reserveQuantity(any(), any());
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
                () -> writer.create(USER_ID, request(5L), null));

        Mockito.verify(bondService, Mockito.never())
                .reserveQuantity(any(), any());
    }

    @Test
    void rejectsBondThatIsSoldOut() {

        Bond bond = bond(BondStatus.SOLD_OUT, 0L);

        givenActiveCustomer();
        givenBond(bond);

        assertThrows(
                BadRequestException.class,
                () -> writer.create(USER_ID, request(5L), null));

        Mockito.verify(bondService, Mockito.never())
                .reserveQuantity(any(), any());
        Mockito.verify(dealConfirmationRepository, Mockito.never())
                .save(any());
    }

    // ============================================================
    // BACK-OFFICE DEALS
    // ============================================================

    @Test
    void createsDealForAClientAndRecordsTheChosenDate() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        Client client = Client.builder()
                .id(UUID.randomUUID())
                .name("Acme Ltd")
                .email("ops@acme.example")
                .build();

        LocalDate dealDate = LocalDate.of(2026, 10, 1);

        User employee = customer();

        Mockito.when(clientRepository.findById(client.getId()))
                .thenReturn(Optional.of(client));
        Mockito.when(userService.getUser(USER_ID))
                .thenReturn(employee);
        Mockito.when(bondService.findBondById(bond.getId()))
                .thenReturn(bond);
        Mockito.when(bondService.reserveQuantity(bond.getId(), BigDecimal.valueOf(500)))
                .thenReturn(1);
        givenReference("2026/S/OCT/1");
        givenSaveReturnsItsArgument();

        DealConfirmationWriter.CreatedDeal created =
                writer.createForClient(
                        client.getId(),
                        bond.getId(),
                        5L,
                        dealDate,
                        null,
                        USER_ID,
                        null,
                        null,
                        DeliveryMode.ICCL,
                        "2610022",
                        DealSide.SELL,
                        null);

        // The deal belongs to the client, not to any account.
        Mockito.verify(dealConfirmationRepository).save(
                Mockito.argThat(deal -> deal.getClient() == client
                        && deal.getCustomer() == null
                        && deal.getCreatedBy() == employee
                        && dealDate.equals(deal.getDealDate())));

        // The letter prints the client and the date the employee chose.
        assertEquals("Acme Ltd", created.documentData().customerName());
        assertEquals("ops@acme.example", created.documentData().customerEmail());
        assertEquals(dealDate, created.documentData().dealDate());
    }

    @Test
    void fillsTheLettersCounterpartyDetailsFromTheChosenAccounts() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        Party party = Party.builder()
                .id(UUID.randomUUID())
                .legalName("Acme Ltd")
                .address("1 Marine Drive, Mumbai")
                .build();

        Client client = Client.builder()
                .id(UUID.randomUUID())
                .name("Acme Ltd")
                .email("ops@acme.example")
                .party(party)
                .build();

        BankAccount bank = BankAccount.builder()
                .id(UUID.randomUUID())
                .bankName("HDFC Bank")
                .branchLocation("Marine Lines")
                .ifscCode("HDFC0001234")
                .accountNumber("cipher")
                .build();

        DematAccount demat = DematAccount.builder()
                .id(UUID.randomUUID())
                .accountHolderName("Acme Broking")
                .dpId("IN300001")
                .clientId("12345678")
                .build();

        Mockito.when(clientRepository.findById(client.getId()))
                .thenReturn(Optional.of(client));
        Mockito.when(userService.getUser(USER_ID))
                .thenReturn(customer());
        Mockito.when(bondService.findBondById(bond.getId()))
                .thenReturn(bond);
        Mockito.when(bondService.reserveQuantity(bond.getId(), BigDecimal.valueOf(500)))
                .thenReturn(1);
        Mockito.when(bankAccountRepository.findByIdAndParty(bank.getId(), party))
                .thenReturn(Optional.of(bank));
        Mockito.when(dematAccountRepository.findByIdAndParty(demat.getId(), party))
                .thenReturn(Optional.of(demat));
        Mockito.when(panDetailsService.plainPan(party))
                .thenReturn(Optional.of("AAKCP1234C"));
        Mockito.when(secretCipher.decrypt("cipher"))
                .thenReturn("123456789");
        givenReference("2026/S/OCT/1");
        givenSaveReturnsItsArgument();

        DealConfirmationWriter.CreatedDeal created = writer.createForClient(
                client.getId(),
                bond.getId(),
                5L,
                LocalDate.of(2026, 10, 1),
                null,
                USER_ID,
                bank.getId(),
                demat.getId(),
                DeliveryMode.DEMAT,
                null,
                DealSide.SELL,
                null);

        CounterpartyDetails details = created.documentData().counterparty();

        assertEquals("AAKCP1234C", details.pan());
        assertEquals("Acme Broking", details.dpName());
        assertEquals("IN300001", details.dpId());
        assertEquals("12345678", details.clientId());
        assertEquals("HDFC Bank", details.bankName());
        assertEquals("1 Marine Drive, Mumbai", details.address());
        assertEquals("Marine Lines", details.bankBranchLocation());
        assertEquals("HDFC0001234", details.bankIfsc());
        assertEquals("123456789", details.bankAccountNumber());
        assertEquals(DeliveryMode.DEMAT, created.documentData().deliveryMode());
    }

    @Test
    void refusesAChosenAccountThatDoesNotBelongToTheClient() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        Party party = Party.builder()
                .id(UUID.randomUUID())
                .legalName("Acme Ltd")
                .build();

        Client client = Client.builder()
                .id(UUID.randomUUID())
                .name("Acme Ltd")
                .party(party)
                .build();

        UUID someoneElsesBank = UUID.randomUUID();

        Mockito.when(clientRepository.findById(client.getId()))
                .thenReturn(Optional.of(client));
        Mockito.when(userService.getUser(USER_ID))
                .thenReturn(customer());
        Mockito.when(bondService.findBondById(bond.getId()))
                .thenReturn(bond);
        Mockito.when(bankAccountRepository.findByIdAndParty(someoneElsesBank, party))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> writer.createForClient(
                        client.getId(),
                        bond.getId(),
                        5L,
                        LocalDate.of(2026, 10, 1),
                        null,
                        USER_ID,
                        someoneElsesBank,
                        null,
                        DeliveryMode.ICCL,
                        "2610022",
                        DealSide.SELL,
                        null));

        // Nothing was reserved: the account was rejected before the UPDATE ran.
        Mockito.verify(bondService, Mockito.never())
                .reserveQuantity(Mockito.any(), Mockito.any());
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
                () -> writer.create(USER_ID, request(5L), null));

        // The row was never even asked to give up units.
        Mockito.verify(bondService, Mockito.never())
                .reserveQuantity(any(), any());
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
        Mockito.when(bondService.reserveQuantity(bond.getId(), BigDecimal.valueOf(500)))
                .thenReturn(0);

        assertThrows(
                ConflictException.class,
                () -> writer.create(USER_ID, request(5L), null));

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

        writer.create(USER_ID, request(5L), null);

        InOrder order = Mockito.inOrder(bondService, dealConfirmationRepository);

        order.verify(bondService).reserveQuantity(bond.getId(), BigDecimal.valueOf(500));
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
                () -> writer.create(USER_ID, request(5L), null));

        /*
         * The exception escapes the @Transactional boundary, which is what makes
         * the reservation roll back with it. That rollback itself is Spring's and
         * the database's; with no embedded database in this project it is not
         * asserted here.
         */
        Mockito.verify(bondService).reserveQuantity(bond.getId(), BigDecimal.valueOf(500));
    }

    // ============================================================
    // VIEW ONLY
    // ============================================================

    @Test
    void previewDescribesTheDealWithoutWritingIt() {

        Bond bond = bond(BondStatus.ACTIVE, 1000L);

        givenActiveCustomer();
        givenBond(bond);

        DealConfirmationWriter.CreatedDeal projected =
                writer.preview(USER_ID, request(5L));

        // Everything a customer reads on the page is filled in.
        assertEquals(ISIN, projected.response().getIsin());
        assertEquals(100L, projected.response().getQuantityPerLot());
        assertEquals(5L, projected.response().getNumberOfLots());
        assertEquals(500L, projected.response().getTotalQuantity());
        assertEquals(
                new BigDecimal("49470.00"),
                projected.response().getTotalAmount());
        assertEquals("Test Bond", projected.documentData().bondName());

        /*
         * The fields only a written deal can have are null rather than invented.
         * The reference especially: it is issued from a table, so producing one
         * here would mean writing a row — and it would be a number no deal holds.
         */
        assertNull(projected.response().getDealConfirmationId());
        assertNull(projected.response().getDealReference());
        assertNull(projected.response().getStatus());
        assertNull(projected.documentData().dealReference());

        /*
         * Nothing was reserved, nothing was written, and no reference was issued —
         * which is the whole point of the read-only path.
         */
        Mockito.verify(bondService, Mockito.never())
                .reserveQuantity(any(), any());
        Mockito.verify(bondService, Mockito.never()).saveBond(any());
        Mockito.verifyNoInteractions(dealConfirmationRepository);
        Mockito.verifyNoInteractions(dealReferenceGenerator);
    }

    @Test
    void previewRefusesARequestAPurchaseWouldRefuse() {

        /*
         * The two paths share their validation on purpose. A preview that accepted
         * a request the purchase rejects would show a customer figures for a deal
         * they cannot have, which is worse than showing them nothing.
         */
        User suspended = customer();
        suspended.setStatus(UserStatus.SUSPENDED);

        Mockito.when(userService.getUser(USER_ID))
                .thenReturn(suspended);

        assertThrows(
                ForbiddenException.class,
                () -> writer.preview(USER_ID, request(5L)));

        Mockito.verifyNoInteractions(bondService);
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
                () -> writer.create(USER_ID, request(5L), null));

        Mockito.verifyNoInteractions(bondService);
    }

    // ============================================================
    // FIXTURES
    // ============================================================

    private CreateDealConfirmationRequest request(long numberOfLots) {
        return new CreateDealConfirmationRequest(ISIN, numberOfLots, null, null);
    }

    /**
     * The lot size lived on the request until it moved onto the bond, so the
     * fixture carries it now: every quantity in this class is 100 units a lot.
     */
    private Bond bond(BondStatus status, Long quantum) {

        return Bond.builder()
                .id(UUID.randomUUID())
                .name("Test Bond")
                .isin(ISIN)
                .status(status)
                .quantum(quantum == null ? null : BigDecimal.valueOf(quantum))
                .lotSize(new BigDecimal("100"))
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
        Mockito.when(bondService.findBond(ISIN)).thenReturn(bond);
    }

    /**
     * Stubs the reservation the way the real conditional UPDATE behaves: it
     * succeeds, and the bond read back afterwards carries the new figure.
     *
     * @return the bond the writer will see after reserving — the one it mutates
     */
    private Bond givenReservationSucceeds(Bond bond, long remainingAfter) {

        Mockito.when(bondService.reserveQuantity(bond.getId(), BigDecimal.valueOf(500)))
                .thenReturn(1);

        Bond afterReservation = bond(bond.getStatus(), remainingAfter);
        afterReservation.setId(bond.getId());
        afterReservation.setPrice(bond.getPrice());

        Mockito.when(bondService.findBondById(bond.getId()))
                .thenReturn(afterReservation);

        return afterReservation;
    }

    private void givenReference(String reference) {
        Mockito.when(dealReferenceGenerator.next(any(LocalDate.class), any(DealSide.class)))
                .thenReturn(reference);
    }

    private void givenSaveReturnsItsArgument() {
        Mockito.when(dealConfirmationRepository.save(any(DealConfirmation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
