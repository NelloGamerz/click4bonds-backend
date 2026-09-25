package com.click4bonds.app.Modules.DealConfirmation.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The values a deal confirmation letter prints, independent of where they go.
 *
 * <p>Separate from the cell map so the arithmetic can be read and tested without
 * a spreadsheet address in sight, and so a template revision that moves a cell
 * does not touch the maths. Every money field is already rounded and in the unit
 * the letter shows — the coupling of quantity, face value and rate happens once,
 * in the factory.</p>
 *
 * @param letterDate              date printed at the top of the letter
 * @param dealReference           our reference for the deal
 * @param counterpartyLine        the "Counterparty Name- ..." line
 * @param transactionType         e.g. "Our Sale"
 * @param modeOfDelivery          e.g. "ICCL"
 * @param dealDate                date the deal was struck
 * @param valueDate               settlement date
 * @param isin                    the bond's ISIN
 * @param price                   clean price per bond
 * @param securityName            the bond's name
 * @param maturityDate            the bond's maturity, or null when perpetual
 * @param ipDateDescription       the bond's raw interest-payment description
 * @param lastInterestPaymentDate last coupon date before the value date
 * @param couponRateFraction      coupon as a fraction: 13.70% prints as 0.1370
 * @param accruedDays             days of interest accrued at settlement
 * @param numberOfBonds           quantity bought
 * @param quantum                 face value of the position
 * @param principalAmount         price value of the position
 * @param accruedInterest         interest component of the consideration
 * @param stampDuty               stamp duty component
 * @param totalConsideration      principal + accrued interest + stamp duty
 */
public record DealConfirmationSheetValues(
        LocalDate letterDate,
        String dealReference,
        String counterpartyLine,
        String transactionType,
        String modeOfDelivery,
        LocalDate dealDate,
        LocalDate valueDate,
        String isin,
        BigDecimal price,
        String securityName,
        LocalDate maturityDate,
        String ipDateDescription,
        LocalDate lastInterestPaymentDate,
        BigDecimal couponRateFraction,
        Long accruedDays,
        Long numberOfBonds,
        BigDecimal quantum,
        BigDecimal principalAmount,
        BigDecimal accruedInterest,
        BigDecimal stampDuty,
        BigDecimal totalConsideration
) {
}
