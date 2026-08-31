package com.click4bonds.app.Modules.ContactUS.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.click4bonds.app.Modules.ContactUS.Models.ContactInquiry;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;

@Repository
public interface ContactInquiryRepository
        extends JpaRepository<ContactInquiry, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<ContactInquiry> findByEmailIgnoreCase(String email);

    Page<ContactInquiry> findAllByOrderByCreatedAtDesc(
            Pageable pageable);

    Page<ContactInquiry> findByStatusOrderByCreatedAtDesc(
            ContactInquiryStatus status,
            Pageable pageable);

}
