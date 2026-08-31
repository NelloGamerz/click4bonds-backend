package com.click4bonds.app.Modules.ContactUS.Service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.click4bonds.app.Modules.Common.Exceptions.ConflictException;
import com.click4bonds.app.Modules.Common.Exceptions.InternalServerException;
import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryRequest;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryResponse;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryAdminResponse;
import com.click4bonds.app.Modules.ContactUS.Models.ContactInquiry;
import com.click4bonds.app.Modules.ContactUS.Repository.ContactInquiryRepository;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ContactInquiryService {

    private final ContactInquiryRepository contactInquiryRepository;

    public ContactInquiryResponse createInquiry(
            ContactInquiryRequest request,
            HttpServletRequest httpRequest) {

        String email = normalizeEmail(request.email());

        log.info(
                "Creating contact inquiry for email domain: {}",
                getEmailDomain(email));

        // Friendly duplicate check
        if (contactInquiryRepository.existsByEmailIgnoreCase(email)) {

            log.warn(
                    "Duplicate contact inquiry attempt for email domain: {}",
                    getEmailDomain(email));

            throw new ConflictException(
                    "A contact request has already been submitted for this email address.");
        }

        ContactInquiry inquiry = ContactInquiry.builder()
                .name(request.name().trim())
                .email(email)
                .phoneNumber(request.phoneNumber().trim())
                .investmentTimeline(request.investmentTimeline())
                .whatAreYouLookingFor(
                        request.whatAreYouLookingFor())
                .otherWhatAreYouLookingFor(
                        normalizeOtherValue(
                                request.otherWhatAreYouLookingFor()))
                .ipAddress(resolveClientIp(httpRequest))
                .userAgent(
                        truncate(
                                httpRequest.getHeader("User-Agent"),
                                1000))
                .referer(
                        truncate(
                                httpRequest.getHeader("Referer"),
                                2000))
                .build();

        try {

            ContactInquiry saved = contactInquiryRepository.save(inquiry);

            log.info(
                    "Contact inquiry created successfully. inquiryId={}, emailDomain={}",
                    saved.getId(),
                    getEmailDomain(email));

            // return ContactInquiryResponse.from(saved);
            return new ContactInquiryResponse(
                    201,
                    saved.getId(),
                    "Thank you. Your request has been submitted successfully.");

        } catch (DataIntegrityViolationException ex) {

            /*
             * Handles race conditions where two requests pass the
             * existsByEmailIgnoreCase() check simultaneously.
             */
            log.warn(
                    "Duplicate contact inquiry detected during database save. emailDomain={}",
                    getEmailDomain(email));

            throw new ConflictException(
                    "A contact request has already been submitted for this email address.");

        } catch (Exception ex) {

            log.error(
                    "Unexpected error while creating contact inquiry. emailDomain={}",
                    getEmailDomain(email),
                    ex);

            throw new InternalServerException(
                    "Unable to submit your contact request at this time. "
                            + "Please try again later.",
                    ex);
        }
    }

    private String normalizeEmail(String email) {

        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOtherValue(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String resolveClientIp(HttpServletRequest request) {

        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {

        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    /**
     * Returns only the email domain for logging.
     * Avoids exposing the customer's complete email address in logs.
     */
    private String getEmailDomain(String email) {

        if (email == null || !email.contains("@")) {
            return "unknown";
        }

        return email.substring(email.indexOf('@') + 1);
    }

    @Transactional(readOnly = true)
    public Page<ContactInquiryAdminResponse> getAllInquiries(
            Pageable pageable) {

        return contactInquiryRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(ContactInquiryAdminResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ContactInquiryAdminResponse> getInquiriesByStatus(
            ContactInquiryStatus status,
            Pageable pageable) {

        return contactInquiryRepository
                .findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(ContactInquiryAdminResponse::from);
    }

    public ContactInquiryAdminResponse updateStatus(
            UUID inquiryId,
            ContactInquiryStatus status) {

        ContactInquiry inquiry = contactInquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contact inquiry not found"));

        inquiry.setStatus(status);

        ContactInquiry saved = contactInquiryRepository.save(inquiry);

        log.info(
                "Contact inquiry status updated. inquiryId={}, status={}",
                saved.getId(),
                saved.getStatus());

        return ContactInquiryAdminResponse.from(saved);
    }

}
