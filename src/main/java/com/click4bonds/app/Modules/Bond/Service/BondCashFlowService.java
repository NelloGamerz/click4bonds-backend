package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;
import java.util.List;

import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowResponse;
import com.click4bonds.app.Modules.Bond.Models.Bond;

public interface BondCashFlowService {

    /**
     * Projects the cash flows and reduces them to the single net amount per
     * date that {@link XirrCalculator} discounts.
     *
     * <p>
     * A priced bond is required: the series opens with the purchase leg.
     */
    List<XirrCalculator.CashFlow> generateCashFlows(
            Bond bond,
            LocalDate calculationDate
    );

    /**
     * Projects the same cash flows as {@link #generateCashFlows}, but keeps the
     * coupon and principal components separate and reports the totals.
     *
     * <p>
     * Unlike the XIRR series this does not require a price. Without a usable
     * price the purchase leg is omitted, so the schedule holds only the
     * coupons and principal the holder receives.
     */
    BondCashFlowResponse generateSchedule(
            Bond bond,
            LocalDate calculationDate
    );
}
