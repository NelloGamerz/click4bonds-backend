package com.click4bonds.app.Modules.Bond.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Bond.Enums.BondDocumentType;
import com.click4bonds.app.Modules.Bond.Models.BondDocument;

/**
 * Documents attached to a bond.
 *
 * <p>Every finder scopes by the bond, and takes the bond's id rather than its
 * ISIN. That is the shape the service needs: a document is only ever reached
 * through the bond it belongs to, and an id it already holds after resolving
 * the ISIN. Reaching a document without its bond would be a way to touch
 * another bond's file.</p>
 */
public interface BondDocumentRepository extends JpaRepository<BondDocument, UUID> {

    /**
     * Everything filed against one bond, newest first.
     *
     * <p>Includes the logo. The listing that a caller sees excludes it, and
     * does so in the query below rather than by filtering here, so the two
     * choices stay visible.</p>
     */
    List<BondDocument> findByBond_IdOrderByCreatedAtDesc(UUID bondId);

    /**
     * One bond's documents of every type except one.
     *
     * <p>Exists for the single exclusion in this module — the logo is a
     * document but is not one of the bond's documents, and asking for "the
     * documents" should not return it.</p>
     */
    List<BondDocument> findByBond_IdAndDocumentTypeNotOrderByCreatedAtDesc(
            UUID bondId,
            BondDocumentType excluded);

    /**
     * One document, but only if it belongs to this bond.
     *
     * <p>The bond is part of the lookup rather than checked afterwards, so a
     * document id from another bond simply does not resolve. A caller therefore
     * cannot tell "not yours" from "does not exist", which is the same
     * indistinguishability the deal endpoints keep.</p>
     */
    Optional<BondDocument> findByIdAndBond_Id(UUID id, UUID bondId);

    /** The bond's logo, of which there is at most one. */
    Optional<BondDocument> findFirstByBond_IdAndDocumentType(
            UUID bondId,
            BondDocumentType documentType);

    /** Removes every document of one type from a bond. Used to replace a logo. */
    void deleteByBond_IdAndDocumentType(UUID bondId, BondDocumentType documentType);
}
