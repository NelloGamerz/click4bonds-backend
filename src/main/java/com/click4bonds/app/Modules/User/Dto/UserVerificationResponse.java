package com.click4bonds.app.Modules.User.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.User.Enums.VerificationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
public class UserVerificationResponse {

    private UUID id;

    private VerificationStatus emailStatus;
    private VerificationStatus phoneStatus;
    private VerificationStatus panStatus;
    private VerificationStatus bankAccountStatus;

    private Instant createdAt;
    private Instant updatedAt;
}
