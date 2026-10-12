package com.click4bonds.app.Modules.Bond.Dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a cached bond read actually stores: the summary the API returns,
 * alongside the bond's identifier.
 *
 * <p>{@link BondResponse} is deliberately only the fields a client is shown, so
 * it carries no {@code id}. The id is still needed server-side — a
 * {@code BOND_VIEW} analytics event is filed against it — and the whole point
 * of the read-through cache is that serving a bond needs no database access, so
 * the id travels in the cached entry rather than being looked up again.</p>
 *
 * <p>Internal to {@link com.click4bonds.app.Modules.Bond.Service.BondService};
 * it is never serialized into a response body.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedBond {

    private UUID id;

    private BondResponse bond;
}
