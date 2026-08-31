package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.click4bonds.app.Modules.Bond.Dto.PrincipalRepayment;
import com.click4bonds.app.Modules.Bond.Models.Bond;

public interface PrincipalRepaymentService {

    List<PrincipalRepayment> generateRepayments(
            Bond bond,
            List<LocalDate> couponDates,
            LocalDate calculationDate
    );
}
