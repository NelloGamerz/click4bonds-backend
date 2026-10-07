package com.click4bonds.app.Modules.ContactUS.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.click4bonds.app.Modules.ContactUS.Models.ContactInquiry;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;

@Repository
public interface ContactInquiryRepository
        extends JpaRepository<ContactInquiry, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<ContactInquiry> findByEmailIgnoreCase(String email);

    /**
     * Pages through inquiries, optionally narrowed to one status.
     *
     * <p>Replaces a pair of methods that differed only in whether the status was
     * filtered, and that each carried {@code OrderByCreatedAtDesc} in the name.
     * That suffix was the problem: Spring Data applies the method name's ordering
     * <em>and</em> the {@code Pageable}'s, so a caller whose pageable already
     * defaulted to {@code createdAt DESC} — the admin endpoint did — had the same
     * sort emitted twice, and a caller who asked to sort by anything else got it
     * appended to a fixed {@code createdAt DESC} rather than instead of it.
     *
     * <p>So this query does not sort. The ordering is the {@code Pageable}'s, in
     * one place, and whatever it says is what the database does.</p>
     *
     * @param status   restrict to this status, or null for every status
     * @param pageable page and ordering request
     * @return one page of matching inquiries
     */
    @Query("""
            SELECT i
            FROM ContactInquiry i
            WHERE (:status IS NULL OR i.status = :status)
            """)
    Page<ContactInquiry> findInquiries(
            @Param("status") ContactInquiryStatus status,
            Pageable pageable);

}
