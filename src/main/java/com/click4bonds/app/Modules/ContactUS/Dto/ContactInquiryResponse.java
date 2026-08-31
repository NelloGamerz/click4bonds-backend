package com.click4bonds.app.Modules.ContactUS.Dto;

import java.time.Instant;
import java.util.UUID;
import com.click4bonds.app.Modules.ContactUS.Models.ContactInquiry;
import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;
import com.click4bonds.app.Modules.ContactUS.enums.InvestmentProduct;
import com.click4bonds.app.Modules.ContactUS.enums.InvestmentTimeline;

public record ContactInquiryResponse(
        UUID id,

        String name,
        String email,
        String phoneNumber,

        InvestmentTimeline investmentTimeline,
        InvestmentProduct whatAreYouLookingFor,
        String otherWhatAreYouLookingFor,

        ContactInquiryStatus status,

        String country,
        String city,

        Instant createdAt,
        Instant updatedAt) {

    public static ContactInquiryResponse from(
            ContactInquiry inquiry) {

        return new ContactInquiryResponse(
                inquiry.getId(),

                inquiry.getName(),
                inquiry.getEmail(),
                inquiry.getPhoneNumber(),

                inquiry.getInvestmentTimeline(),
                inquiry.getWhatAreYouLookingFor(),
                inquiry.getOtherWhatAreYouLookingFor(),

                inquiry.getStatus(),

                inquiry.getCountry(),
                inquiry.getCity(),

                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt());
    }
}
