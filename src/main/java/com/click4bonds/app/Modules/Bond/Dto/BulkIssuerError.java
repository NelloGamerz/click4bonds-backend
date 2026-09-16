package com.click4bonds.app.Modules.Bond.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Failure of a single row in a bulk issuer import.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkIssuerError {

    /**
     * 1-based position of the row in the submitted request.
     */
    private Integer row;

    private String isin;

    private String message;
}
