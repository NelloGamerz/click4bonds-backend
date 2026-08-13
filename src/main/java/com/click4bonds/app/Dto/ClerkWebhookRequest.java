package com.click4bonds.app.Dto;

import java.util.List;

public record ClerkWebhookRequest(
        String type,
        ClerkUserData data
) {

    public record ClerkUserData(
            String id,
            String first_name,
            String last_name,
            String image_url,
            String profile_image_url,
            List<EmailAddress> email_addresses
    ) {
    }


    public record EmailAddress(
            String email_address
    ) {
    }
}

