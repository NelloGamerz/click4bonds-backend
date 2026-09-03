package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;
import java.util.List;

import com.click4bonds.app.Modules.Bond.Dto.CouponPayment;
import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Models.Bond;

public interface CouponCalculationService {

    List<CouponPayment> calculateCoupons(
            Bond bond,
            List<LocalDate> couponDates,
            List<PrincipalRepayment> principalRepayments,
            LocalDate calculationDate
    );
}