package com.click4bonds.app.Modules.ContactUS.Dto;

import java.util.UUID;

public record ContactInquiryResponse(
        int statusCode,
        UUID inquiryId,
        String message) {
}
