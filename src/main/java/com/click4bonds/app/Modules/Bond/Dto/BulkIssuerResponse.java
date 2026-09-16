package com.click4bonds.app.Modules.Bond.Dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a bulk issuer import.
 *
 * Every row is processed independently, so a bad row does
 * not discard the rows that were imported successfully.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkIssuerResponse {

    private int totalRequested;

    private int createdCount;

    private int updatedCount;

    private int failedCount;

    private List<IssuerResponse> issuers;

    private List<BulkIssuerError> errors;
}
