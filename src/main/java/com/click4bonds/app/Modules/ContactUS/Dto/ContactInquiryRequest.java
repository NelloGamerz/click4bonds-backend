package com.click4bonds.app.Modules.ContactUS.Dto;

import com.click4bonds.app.Modules.ContactUS.enums.InvestmentTimeline;
import com.click4bonds.app.Modules.ContactUS.enums.InvestmentProduct;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContactInquiryRequest(

        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must not exceed 100 characters") String name,

        @NotBlank(message = "Email is required") @Email(message = "Invalid email address") @Size(max = 320, message = "Email must not exceed 320 characters") String email,

        @NotBlank(message = "Phone number is required") @Size(max = 30, message = "Phone number must not exceed 30 characters") String phoneNumber,

        @NotNull(message = "Investment timeline is required") InvestmentTimeline investmentTimeline,

        @NotNull(message = "What are you looking for is required") InvestmentProduct whatAreYouLookingFor,

        @Size(max = 500, message = "Other investment details must not exceed 500 characters") String otherWhatAreYouLookingFor) {

    public ContactInquiryRequest {
        if (whatAreYouLookingFor == InvestmentProduct.OTHER) {
            if (otherWhatAreYouLookingFor == null || otherWhatAreYouLookingFor.isBlank()) {
                throw new IllegalArgumentException(
                        "Other investment details are required when 'OTHER' is selected");
            }
        } else {
            if (otherWhatAreYouLookingFor != null && !otherWhatAreYouLookingFor.isBlank()) {
                throw new IllegalArgumentException(
                        "Other investment details should only be provided when 'OTHER' is selected");
            }
        }
    }
}
