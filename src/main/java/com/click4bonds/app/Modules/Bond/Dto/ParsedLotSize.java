package com.click4bonds.app.Modules.Bond.Dto;

import java.math.BigDecimal;

import com.click4bonds.app.Modules.Bond.Enums.LotSizeType;

/**
 * The two normalized lot-size columns derived from a raw lot description.
 *
 * @param lotSize     units per lot, or {@code null} for DEMAT / SGL where the
 *                    lot carries no numeric size
 * @param lotSizeType the kind of lot the text described
 */
public record ParsedLotSize(BigDecimal lotSize, LotSizeType lotSizeType) {

    public static ParsedLotSize of(BigDecimal lotSize, LotSizeType lotSizeType) {
        return new ParsedLotSize(lotSize, lotSizeType);
    }

    public static ParsedLotSize unknown() {
        return new ParsedLotSize(null, LotSizeType.UNKNOWN);
    }
}
