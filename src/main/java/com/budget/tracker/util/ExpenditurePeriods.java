package com.budget.tracker.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExpenditurePeriods {

    public static final String YESTERDAY = "yesterday";
    public static final String TODAY = "today";
    public static final String LAST_WEEK = "lastWeek";
    public static final String THIS_WEEK = "thisWeek";
    public static final String LAST_MONTH = "lastMonth";
    public static final String THIS_MONTH = "thisMonth";

    public record Range(String name, LocalDate startDate, LocalDate endDate) {
    }

    private static final WeekFields ISO = WeekFields.ISO;
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

    private ExpenditurePeriods() {
    }

    /** All six ranges for "now" in the app zone, in display order. */
    public static Map<String, Range> all() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        LocalDate thisMonday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth lastMonth = thisMonth.minusMonths(1);

        Map<String, Range> ranges = new LinkedHashMap<>();
        ranges.put(YESTERDAY, new Range(YESTERDAY, today.minusDays(1), today.minusDays(1)));
        ranges.put(TODAY, new Range(TODAY, today, today));
        ranges.put(LAST_WEEK, new Range(LAST_WEEK, thisMonday.minusWeeks(1), thisMonday.minusDays(1)));
        ranges.put(THIS_WEEK, new Range(THIS_WEEK, thisMonday, thisMonday.plusWeeks(1).minusDays(1)));
        ranges.put(LAST_MONTH, new Range(LAST_MONTH, lastMonth.atDay(1), lastMonth.atEndOfMonth()));
        ranges.put(THIS_MONTH, new Range(THIS_MONTH, thisMonth.atDay(1), thisMonth.atEndOfMonth()));
        return ranges;
    }

    /** ISO week key (e.g. 2026-W34) for a date in the app zone. */
    public static String weekKey(LocalDate date) {
        return date.get(ISO.weekBasedYear()) + "-W" + String.format("%02d", date.get(ISO.weekOfWeekBasedYear()));
    }

    /** Calendar month key (e.g. 2026-08) for a date in the app zone. */
    public static String monthKey(LocalDate date) {
        return date.format(MONTH_KEY);
    }
}
