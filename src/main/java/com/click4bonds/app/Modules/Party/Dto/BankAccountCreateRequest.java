package com.click4bonds.app.Modules.Party.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A bank account submission.
 *
 * <p>The account number format is deliberately conservative — digits only,
 * six to twenty of them. A number that does not fit is refused rather than
 * stored, because a financial account the business cannot use is worse than a
 * clear rejection.</p>
 */
public record BankAccountCreateRequest(

        @NotBlank(message = "Account holder name is required")
        @Size(max = 255, message = "Account holder name must not exceed 255 characters")
        String accountHolderName,

        @NotBlank(message = "Bank name is required")
        @Size(max = 255, message = "Bank name must not exceed 255 characters")
        String bankName,

        @NotBlank(message = "Account number is required")
        @Pattern(regexp = "^[0-9]{6,20}$", message = "Account number must be 6 to 20 digits")
        String accountNumber,

        @NotBlank(message = "IFSC code is required")
        @Pattern(
                regexp = "^[A-Z]{4}0[A-Z0-9]{6}$",
                message = "IFSC code must be four letters, a zero and six alphanumeric characters")
        String ifscCode,

        Boolean primaryAccount) {
}
