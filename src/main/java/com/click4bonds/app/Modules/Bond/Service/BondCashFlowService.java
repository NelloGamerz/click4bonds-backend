package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;
import java.util.List;

import com.click4bonds.app.Modules.Bond.Models.Bond;

public interface BondCashFlowService {

    List<XirrCalculator.CashFlow> generateCashFlows(
            Bond bond,
            LocalDate calculationDate
    );
}