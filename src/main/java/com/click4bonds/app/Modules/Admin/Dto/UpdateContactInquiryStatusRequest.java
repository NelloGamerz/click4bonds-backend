package com.click4bonds.app.Modules.Admin.Dto;

import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateContactInquiryStatusRequest(
        @NotNull ContactInquiryStatus status) {
}
