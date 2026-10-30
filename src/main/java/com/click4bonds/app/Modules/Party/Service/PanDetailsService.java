package com.click4bonds.app.Modules.Party.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Party.Dto.PanDetailsResponse;
import com.click4bonds.app.Modules.Party.Model.PanDetails;
import com.click4bonds.app.Modules.Party.Model.Party;
import com.click4bonds.app.Modules.Party.Repository.PanDetailsRepository;
import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PAN submission and verification.
 *
 * <p>The PAN value is encrypted before it is stored and decrypted when a party
 * response is built. It is never logged.</p>
 *
 * <p>Submitting a PAN puts the record at {@link VerificationStatus#PENDING}.
 * Only {@link #updateStatus} can move it to {@code VERIFIED}, and that method is
 * reached solely from the admin-authorized verification surface. Format
 * validation in the request DTO is not verification and never changes status.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PanDetailsService {

    private final PanDetailsRepository panDetailsRepository;
    private final SecretCipher secretCipher;

    /**
     * Adds or replaces the PAN on a party.
     *
     * <p>A verified PAN is not replaceable through this path: doing so would
     * silently discard the proof that made the party verified. An operator who
     * needs to change a verified PAN must first set its status back through the
     * authorized workflow.</p>
     */
    public PanDetailsResponse submit(Party party, String panNumber) {

        return save(party, panNumber, VerificationStatus.PENDING, false);
    }

    /**
     * Records a PAN entered by the back office as already verified.
     *
     * <p>An employee or admin entering these details is recording what they
     * have checked in the source documents, so the submission and the
     * verification are one act. This is the counterpart of the customer path,
     * which records a claim waiting on that check. It may replace an existing
     * PAN because correcting staff entry is the whole point.</p>
     */
    public PanDetailsResponse submitVerified(Party party, String panNumber) {

        return save(party, panNumber, VerificationStatus.VERIFIED, true);
    }

    private PanDetailsResponse save(
            Party party,
            String panNumber,
            VerificationStatus status,
            boolean allowReplaceVerified) {

        String pan = panNumber.trim().toUpperCase(Locale.ROOT);

        PanDetails details = panDetailsRepository.findByParty(party)
                .orElseGet(() -> PanDetails.builder().party(party).build());

        if (details.getStatus() == VerificationStatus.VERIFIED && !allowReplaceVerified) {
            throw new ConflictException(
                    "This party's PAN is already verified; it cannot be resubmitted");
        }

        details.setPanNumber(secretCipher.encrypt(pan));
        details.setStatus(status);
        details.setVerifiedAt(status == VerificationStatus.VERIFIED ? Instant.now() : null);

        details = panDetailsRepository.save(details);

        // The identifier is logged; the PAN is not.
        log.info("PAN recorded for party {} as {}", party.getId(), status);

        return PanDetailsResponse.of(details, pan);
    }

    public Optional<PanDetailsResponse> find(Party party) {

        return panDetailsRepository.findByParty(party)
                .map(details -> PanDetailsResponse.of(
                        details,
                        secretCipher.decrypt(details.getPanNumber())));
    }

    /**
     * The stored PAN in plaintext, for a document that has to print it.
     *
     * <p>The only caller is the deal confirmation letter. Nothing may log the
     * result.</p>
     */
    public Optional<String> plainPan(Party party) {

        return panDetailsRepository.findByParty(party)
                .map(details -> secretCipher.decrypt(details.getPanNumber()));
    }

    public boolean isVerified(Party party) {

        return panDetailsRepository.findByParty(party)
                .map(details -> details.getStatus() == VerificationStatus.VERIFIED)
                .orElse(false);
    }

    /** Records the outcome of an authorized PAN verification. */
    public PanDetailsResponse updateStatus(Party party, VerificationStatus status) {

        if (status == VerificationStatus.NOT_STARTED) {
            throw new BadRequestException("A verification outcome cannot be NOT_STARTED");
        }

        PanDetails details = panDetailsRepository.findByParty(party)
                .orElseThrow(() -> new BadRequestException(
                        "No PAN has been submitted for this party"));

        details.setStatus(status);
        details.setVerifiedAt(status == VerificationStatus.VERIFIED ? Instant.now() : null);

        details = panDetailsRepository.save(details);

        log.info("PAN verification for party {} set to {}", party.getId(), status);

        return PanDetailsResponse.of(details, secretCipher.decrypt(details.getPanNumber()));
    }
}
