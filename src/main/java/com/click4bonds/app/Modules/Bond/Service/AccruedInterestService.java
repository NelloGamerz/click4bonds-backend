package com.click4bonds.app.Modules.Bond.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Models.Bond;

public interface AccruedInterestService {

    BigDecimal calculate(Bond bond, LocalDate calculationDate);
}