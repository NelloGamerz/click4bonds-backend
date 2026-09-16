package com.click4bonds.app.Modules.Bond.Dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * One row of a bulk issuer import.
 *
 * The bond is identified by its ISIN, so the caller does
 * not need to know internal bond ids.
 */
@Data
public class BondIssuerBulkItem {

    @NotBlank
    @Size(max = 12)
    private String isin;

    @Valid
    @NotNull
    private CreateIssuerRequest issuer;
}
