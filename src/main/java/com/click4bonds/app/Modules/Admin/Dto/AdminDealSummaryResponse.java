package com.click4bonds.app.Modules.Admin.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationSheetValues;
import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One row of the employee's own deals listing.
 *
 * <p>Reads the bond and client, so both must already be fetched — the repository
 * query graph does that.</p>
 */
public record AdminDealSummaryResponse(
        UUID id,
        String dealReference,
        String clientName,
        String bondName,
        String isin,
        Long numberOfLots,
        Long totalQuantity,
        BigDecimal pricePerUnit,
        BigDecimal totalAmount,

        /** The yield saved for this deal, or null until one is calculated. */
        BigDecimal annualYtm,

        /** The semi-annual equivalent of {@link #annualYtm}. */
        BigDecimal semiYtm,

        DealConfirmationStatus status,
        LocalDate dealDate,
        Instant createdAt,

        /**
         * The figures printed on this deal's confirmation letter, as that letter
         * computes them: accrued interest, stamp duty, the total consideration
         * and TDS.
         *
         * <p>A G-Sec is lettered on the G-Sec sheet, which charges TDS on the
         * interest and no stamp duty; a corporate bond is lettered on the
         * corporate sheet, which is the other way round. Each field below is the
         * figure <em>that</em> sheet prints, and the sheet's own charge is the
         * only one serialized: a G-Sec row has no {@code stampDuty} key, a
         * corporate row no {@code tds} key. Null when the deal cannot support the
         * arithmetic — no price or no accrued interest — which is the same
         * best-effort answer the create response gives.</p>
         *
         * <p>Computed on read, so they follow the bond's schedule as it stands
         * now rather than a snapshot taken at purchase time.</p>
         */
        BigDecimal accruedInterest,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal stampDuty,
        BigDecimal totalConsideration,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal tds) {

    public static AdminDealSummaryResponse from(
            DealConfirmation deal,
            DealConfirmationSheetValues values,
            boolean sovereign) {

        return new AdminDealSummaryResponse(
                deal.getId(),
                deal.getDealReference(),
                deal.getClient() == null ? null : deal.getClient().getName(),
                deal.getBond() == null ? null : deal.getBond().getName(),
                deal.getIsin(),
                deal.getNumberOfLots(),
                deal.getTotalQuantity(),
                deal.getPricePerUnit(),
                deal.getTotalAmount(),
                deal.getAnnualYtm(),
                deal.getSemiYtm(),
                deal.getStatus(),
                deal.getDealDate(),
                deal.getCreatedAt(),
                sovereign
                        ? (values == null ? null : values.gsecAccruedInterest())
                        : (values == null ? null : values.accruedInterest()),
                sovereign || values == null ? null : values.stampDuty(),
                sovereign
                        ? (values == null ? null : values.gsecTotalConsiderationAmount())
                        : (values == null ? null : values.totalConsideration()),
                sovereign && values != null ? values.gsecTds() : null);
    }
}
