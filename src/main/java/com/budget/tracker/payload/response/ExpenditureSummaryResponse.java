package com.budget.tracker.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ExpenditureSummaryResponse {
    private BigDecimal yesterday;
    private BigDecimal today;
    private BigDecimal lastWeek;
    private BigDecimal thisWeek;
    private BigDecimal lastMonth;
    private BigDecimal thisMonth;

    private List<LabelPeriodTotal> yesterdayByLabel = List.of();
    private List<LabelPeriodTotal> todayByLabel = List.of();
    private List<LabelPeriodTotal> lastWeekByLabel = List.of();
    private List<LabelPeriodTotal> thisWeekByLabel = List.of();
    private List<LabelPeriodTotal> lastMonthByLabel = List.of();
    private List<LabelPeriodTotal> thisMonthByLabel = List.of();
}
