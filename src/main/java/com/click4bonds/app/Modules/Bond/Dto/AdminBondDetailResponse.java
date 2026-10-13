package com.click4bonds.app.Modules.Bond.Dto;

import java.time.Instant;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A bond as an admin sees it.
 *
 * <p>Every field {@link BondDetailResponse} carries, plus the provenance
 * columns that record where the row came from: the file it was imported from,
 * the row within that file, and when the import ran. Those three are what lets
 * an admin trace a wrong bond back to the sheet it arrived on, and they are
 * why this type exists at all — the public detail view has no business
 * publishing a customer's catalogue's internal filenames.</p>
 *
 * <p>It extends the public detail rather than repeating it so the two cannot
 * drift: a column added to the bond's detail view reaches the admin's
 * automatically, and the only thing to keep in step is the provenance below.
 * The inherited fields are {@code @SuperBuilder} fields, so the admin builder
 * still sets them — see {@code BondService#mapToAdminDetail}.</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AdminBondDetailResponse extends BondDetailResponse {

    /**
     * Name of the file this bond was imported from, e.g. {@code bonds-oct.xlsx}.
     *
     * <p>NULL for a bond created through the API rather than imported.</p>
     */
    private String sourceFileName;

    /**
     * Row within {@link #sourceFileName} that this bond came from.
     *
     * <p>NULL for a bond created through the API rather than imported.</p>
     */
    private Integer sourceRowNumber;

    /**
     * When the import that produced this row ran.
     *
     * <p>NULL for a bond created through the API rather than imported.</p>
     */
    private Instant importedAt;
}
