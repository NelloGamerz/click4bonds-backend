package com.click4bonds.app.Modules.Party.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Party.Dto.PanDetailsResponse;
import com.click4bonds.app.Modules.Party.Model.PanDetails;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Repository.PanDetailsRepository;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

class PanDetailsServiceTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("click4bonds-test-party-key-32by!".getBytes(StandardCharsets.UTF_8));

    private final PanDetailsRepository repository = mock(PanDetailsRepository.class);

    private final SecretCipher cipher = new SecretCipher(KEY);

    private final PanDetailsService service = new PanDetailsService(repository, cipher);

    private final Party party = Party.builder().id(UUID.randomUUID()).legalName("Test").build();

    private final AtomicReference<PanDetails> lastSaved = new AtomicReference<>();

    private PanDetailsServiceTest() {

        when(repository.save(any(PanDetails.class)))
                .thenAnswer(invocation -> {
                    PanDetails saved = invocation.getArgument(0);
                    lastSaved.set(saved);
                    return saved;
                });
    }

    @Test
    void aSubmittedPanIsStoredEncryptedPendingAndReturnedDecrypted() {

        when(repository.findByParty(party)).thenReturn(Optional.empty());

        PanDetailsResponse response = service.submit(party, "aaapz1234c");

        assertEquals(VerificationStatus.PENDING, response.status());
        assertEquals("AAAPZ1234C", response.panNumber());
    }

    @Test
    void theStoredValueIsCiphertextNotThePan() {

        when(repository.findByParty(party)).thenReturn(Optional.empty());

        service.submit(party, "AAAPZ1234C");

        PanDetails stored = lastSaved.get();

        assertNotEquals("AAAPZ1234C", stored.getPanNumber());
        assertEquals("AAAPZ1234C", cipher.decrypt(stored.getPanNumber()));
    }

    @Test
    void staffEntryIsVerifiedImmediatelyAndMayReplaceAnExistingPan() {

        PanDetails existing = PanDetails.builder()
                .party(party)
                .panNumber(cipher.encrypt("AAAPZ1234C"))
                .status(VerificationStatus.VERIFIED)
                .build();

        when(repository.findByParty(party)).thenReturn(Optional.of(existing));

        PanDetailsResponse response = service.submitVerified(party, "AAAPZ9999C");

        assertEquals(VerificationStatus.VERIFIED, response.status());
        assertNotNull(response.verifiedAt());
        assertEquals("AAAPZ9999C", response.panNumber());
    }

    @Test
    void aVerifiedPanCannotBeResubmitted() {
        PanDetails verified = PanDetails.builder()
                .party(party)
                .panNumber(cipher.encrypt("AAAPZ1234C"))
                .status(VerificationStatus.VERIFIED)
                .build();

        when(repository.findByParty(party)).thenReturn(Optional.of(verified));

        assertThrows(ConflictException.class,
                () -> service.submit(party, "AAAPZ9999C"));
    }

    @Test
    void aVerificationSetsAndClearsTheVerifiedTimestamp() {

        PanDetails details = PanDetails.builder()
                .party(party)
                .panNumber(cipher.encrypt("AAAPZ1234C"))
                .status(VerificationStatus.PENDING)
                .build();

        when(repository.findByParty(party)).thenReturn(Optional.of(details));

        service.updateStatus(party, VerificationStatus.VERIFIED);
        assertNotNull(details.getVerifiedAt());
        assertTrue(service.isVerified(party));

        service.updateStatus(party, VerificationStatus.REJECTED);
        assertNull(details.getVerifiedAt());
        assertFalse(service.isVerified(party));
    }

    @Test
    void aMissingPanCannotBeVerified() {

        when(repository.findByParty(party)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> service.updateStatus(party, VerificationStatus.VERIFIED));
    }
}
