package com.click4bonds.app.Modules.Bond.Models;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.click4bonds.app.Modules.Bond.Enums.BondDocumentType;
import com.click4bonds.app.Modules.User.Model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A file that belongs to a bond: its information memorandum, its term sheet, a
 * rating report, its logo.
 *
 * <p><strong>The bytes are not here.</strong> This row records that a document
 * exists, what it is called and where in the object store it lives; the file
 * itself is in Cloudflare R2. That is what keeps a 20 MB memorandum out of the
 * database and out of every backup of it.</p>
 *
 * <p><strong>{@link #storageKey} is a key, not an address</strong>, matching
 * {@link Bond#getLogoKey()} and for the same reason: it names the object
 * without pinning the row to whichever endpoint and bucket happened to be
 * configured when it was written. Reads and deletions resolve it against the
 * current configuration, and a mismatch fails loudly rather than fetching from
 * somewhere else.</p>
 *
 * <p><strong>A document is appended, never overwritten.</strong> One bond may
 * hold several documents of the same type — two rating reports from different
 * agencies, a revised term sheet — so a row is created per upload and the
 * objective is a list, not a slot. {@link BondDocumentType#LOGO} is the single
 * exception, and it is handled by the service that owns the invariant rather
 * than by a constraint here.</p>
 *
 * <p>{@link #storageKey} is unique across the table. Every key carries a random
 * component, so a collision means the same object was confirmed twice, which is
 * a mistake worth failing on rather than two rows quietly describing one
 * file.</p>
 */
@Entity
@Table(name = "bond_documents", indexes = {
        @Index(name = "idx_bond_document_bond", columnList = "bond_id"),
        @Index(name = "idx_bond_document_type", columnList = "document_type"),
        @Index(name = "idx_bond_document_storage_key", columnList = "storage_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BondDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The bond this belongs to.
     *
     * <p>A required relationship, not a loose id: a document about no bond is
     * not a thing, and the foreign key is what stops one being left behind when
     * a bond is removed.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bond_id", nullable = false)
    private Bond bond;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private BondDocumentType documentType;

    /**
     * The name to offer a person downloading this, e.g.
     * {@code ATSPL-IM-2026.pdf}.
     *
     * <p>Kept because the object key is deliberately unreadable — it is a UUID
     * with an extension — and a download named after it would tell the person
     * saving it nothing about what they had. This is record-keeping, not
     * storage: the key is what finds the bytes, and this is what the bytes are
     * called.</p>
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /**
     * Where the object lives, relative to the configured bucket.
     *
     * <p>See the class javadoc: a key, not an address.</p>
     */
    @Column(name = "storage_key", nullable = false, length = 512, unique = true)
    private String storageKey;

    /** MIME type the object was stored as, which is how it is served back. */
    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    /** Size in bytes, as the object store reported it when the upload settled. */
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /**
     * The staff member who attached it.
     *
     * <p>Nullable because a bond's documents may outlive the account that filed
     * them, and because an account may be removed without its uploads becoming
     * untrue.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
