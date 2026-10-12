package com.click4bonds.app.Modules.Bond.Dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a cached bond read actually stores: the bond in full, alongside its
 * identifier.
 *
 * <p>{@link BondDetailResponse} already carries the id as a field, but not in a
 * place a reader can rely on — a bond row with no persisted id, or a cached entry
 * written before that field existed, would leave a caller with nothing. The id is
 * what a {@code BOND_VIEW} analytics event is filed against, and the whole point
 * of the read-through cache is that serving a bond needs no database access, so
 * it is stored explicitly rather than picked out of the payload.</p>
 *
 * <p>Internal to {@link com.click4bonds.app.Modules.Bond.Service.BondService};
 * it is never serialized into a response body.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedBond {

    private UUID id;

    private BondDetailResponse bond;
}
