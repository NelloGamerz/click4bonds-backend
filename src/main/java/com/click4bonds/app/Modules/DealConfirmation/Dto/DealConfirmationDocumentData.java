package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.click4bonds.app.Modules.DealConfirmation.Enums.DealConfirmationStatus;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * Everything the future deal confirmation document needs, in one flat record.
 *
 * <p>This is the seam for the Excel template step that is not implemented yet:
 * when the template is filled, it is filled from this record, and the template
 * code never sees an entity, a repository or a session. It is assembled inside
 * the transaction that creates the deal, so the document step — which runs after
 * that transaction has committed — never touches a lazy association.</p>
 *
 * <p>Values are snapshots taken at purchase time; they do not follow later edits
 * to the bond.</p>
 */
public record DealConfirmationDocumentData(

        String dealReference,

        LocalDate dealDate,

        Instant dealCreatedAt,

        String customerName,

        String customerEmail,

        String bondName,

        String isin,

        /** {@code SecurityType} name, or null when the bond has none. */
        String securityType,

        BigDecimal couponRate,

        LocalDate maturityDate,

        Long quantityPerLot,

        Long numberOfLots,

        Long totalQuantity,

        BigDecimal pricePerUnit,

        BigDecimal totalAmount,

        DealConfirmationStatus status
) {

    /**
     * Builds the snapshot from a deal.
     *
     * <p>Must be called while the persistence context is still open — it reads
     * {@code customer} and {@code bond}, both of which are lazily loaded.</p>
     */
    public static DealConfirmationDocumentData from(DealConfirmation deal) {

        var bond = deal.getBond();

        return new DealConfirmationDocumentData(
                deal.getDealReference(),
                deal.getCreatedAt() == null
                        ? LocalDate.now()
                        : LocalDate.ofInstant(deal.getCreatedAt(), ZoneOffset.UTC),
                deal.getCreatedAt(),
                customerName(deal.getCustomer()),
                deal.getCustomer() == null ? null : deal.getCustomer().getEmail(),
                bond == null ? null : bond.getName(),
                deal.getIsin(),
                bond == null || bond.getSecurityType() == null
                        ? null
                        : bond.getSecurityType().name(),
                bond == null ? null : bond.getCouponRate(),
                bond == null ? null : bond.getMaturityDate(),
                deal.getQuantityPerLot(),
                deal.getNumberOfLots(),
                deal.getTotalQuantity(),
                deal.getPricePerUnit(),
                deal.getTotalAmount(),
                deal.getStatus());
    }

    private static String customerName(User customer) {

        if (customer == null) {
            return null;
        }

        String name = ((customer.getFirstName() == null ? "" : customer.getFirstName())
                + " "
                + (customer.getLastName() == null ? "" : customer.getLastName())).trim();

        /*
         * A user whose profile has no name yet is still a real counterparty, so
         * fall back to the identifier the customer knows: their email.
         */
        return name.isEmpty() ? customer.getEmail() : name;
    }
}
