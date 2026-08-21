package com.budget.tracker.web;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class CurrencyFormatter {

    public static final String DEFAULT_SYMBOL = "₹";

    private final String symbol;
    private final NumberFormat numberFormat;

    public CurrencyFormatter(String symbol) {
        this.symbol = (symbol == null || symbol.isBlank()) ? DEFAULT_SYMBOL : symbol;
        // en-IN grouping matches the React frontend's Intl.NumberFormat('en-IN')
        this.numberFormat = NumberFormat.getInstance(new Locale("en", "IN"));
        this.numberFormat.setMinimumFractionDigits(2);
        this.numberFormat.setMaximumFractionDigits(2);
    }

    public String format(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        String sign = value.signum() < 0 ? "-" : "";
        return sign + symbol + numberFormat.format(value.abs());
    }
}
