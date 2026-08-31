package com.click4bonds.app.Modules.Bond.Service;

import java.time.LocalDate;

import com.click4bonds.app.Modules.Bond.Dto.MaturitySchedule;

public interface MaturityDescriptionParser {
    MaturitySchedule parse(String maturityDescription, LocalDate normalizedMaturityDate);
}
