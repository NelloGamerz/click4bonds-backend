package com.click4bonds.app.Modules.Party.Dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A partial update of a bank account. Null fields are left unchanged.
 *
 * <p>Status is absent on purpose: the customer who owns the account cannot
 * declare it verified. That is an authorized admin operation.</p>
 */
public record BankAccountUpdateRequest(

        @Size(max = 255, message = "Account holder name must not exceed 255 characters")
        String accountHolderName,

        @Size(max = 255, message = "Bank name must not exceed 255 characters")
        String bankName,

        @Size(max = 255, message = "Branch location must not exceed 255 characters")
        String branchLocation,

        @Pattern(regexp = "^[0-9]{6,20}$", message = "Account number must be 6 to 20 digits")
        String accountNumber,

        @Pattern(
                regexp = "^[A-Z]{4}0[A-Z0-9]{6}$",
                message = "IFSC code must be four letters, a zero and six alphanumeric characters")
        String ifscCode,

        Boolean primaryAccount) {
}
