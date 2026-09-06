package com.budget.tracker.payload.response;

import java.math.BigDecimal;

public record LabelPeriodTotal(String labelName, BigDecimal amount) {
}
