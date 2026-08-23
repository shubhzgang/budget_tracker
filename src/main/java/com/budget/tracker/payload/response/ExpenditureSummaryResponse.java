package com.budget.tracker.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ExpenditureSummaryResponse {
    private BigDecimal yesterday;
    private BigDecimal today;
    private BigDecimal lastWeek;
    private BigDecimal thisWeek;
    private BigDecimal lastMonth;
    private BigDecimal thisMonth;
}
