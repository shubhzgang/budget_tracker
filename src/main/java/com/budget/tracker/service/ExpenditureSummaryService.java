package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.ExpenditurePeriodTotal;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.payload.response.ExpenditureSummaryResponse;
import com.budget.tracker.payload.response.LabelPeriodTotal;
import com.budget.tracker.repository.ExpenditurePeriodTotalRepository;
import com.budget.tracker.util.ExpenditurePeriods;
import com.budget.tracker.util.TimeZones;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ExpenditureSummaryService {

    public static final List<TransactionType> EXPENDITURE_TYPES = List.of(TransactionType.EXPENSE, TransactionType.LEND);

    private final ExpenditurePeriodTotalRepository periodRepository;

    public ExpenditureSummaryService(ExpenditurePeriodTotalRepository periodRepository) {
        this.periodRepository = periodRepository;
    }

    private UUID getCurrentUserId() {
        UUID userId = AuthContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("No authenticated user found in context");
        }
        return userId;
    }

    // ── Eager maintenance (write path) ────────────────────────────────────

    @Transactional
    public void recordExpenditure(UUID userId, OffsetDateTime transactionDate, TransactionType type, BigDecimal amount,
                                  Set<String> labelNames) {
        if (!isExpenditure(type)) return;
        LocalDate date = transactionDate.atZoneSameInstant(TimeZones.APP_ZONE).toLocalDate();
        Adjuster overall = new Adjuster(userId, date, amount);
        overall.adjust();
        Adjuster perLabel = new Adjuster(userId, date, amount, labelNames);
        perLabel.adjust();
    }

    @Transactional
    public void removeExpenditure(UUID userId, OffsetDateTime transactionDate, TransactionType type, BigDecimal amount,
                                  Set<String> labelNames) {
        if (!isExpenditure(type)) return;
        LocalDate date = transactionDate.atZoneSameInstant(TimeZones.APP_ZONE).toLocalDate();
        Adjuster overall = new Adjuster(userId, date, amount.negate());
        overall.adjust();
        Adjuster perLabel = new Adjuster(userId, date, amount.negate(), labelNames);
        perLabel.adjust();
    }

    /** Rebuild a user's stored week/month totals from their transactions (bulk loads, backfill). */
    @Transactional
    public void recomputeForUser(UUID userId) {
        periodRepository.deleteAllByUserId(userId);
        Map<String, BigDecimal> weeks = new HashMap<>();
        Map<String, BigDecimal> months = new HashMap<>();
        Map<String, BigDecimal> labelWeeks = new HashMap<>();
        Map<String, BigDecimal> labelMonths = new HashMap<>();

        for (Object[] row : periodRepository.findExpenditureDateAmounts(userId, EXPENDITURE_TYPES)) {
            LocalDate date = ((OffsetDateTime) row[0]).atZoneSameInstant(TimeZones.APP_ZONE).toLocalDate();
            BigDecimal amount = (BigDecimal) row[1];
            weeks.merge(ExpenditurePeriods.weekKey(date), amount, BigDecimal::add);
            months.merge(ExpenditurePeriods.monthKey(date), amount, BigDecimal::add);
        }
        for (Object[] row : periodRepository.findExpenditureDateAmountsWithLabels(userId, EXPENDITURE_TYPES)) {
            LocalDate date = ((OffsetDateTime) row[0]).atZoneSameInstant(TimeZones.APP_ZONE).toLocalDate();
            BigDecimal amount = (BigDecimal) row[1];
            String name = (String) row[2];
            labelWeeks.merge(ExpenditurePeriods.weekKey(date) + ":" + name, amount, BigDecimal::add);
            labelMonths.merge(ExpenditurePeriods.monthKey(date) + ":" + name, amount, BigDecimal::add);
        }
        for (Object[] row : periodRepository.findExpenditureDateAmountsUnlabelled(userId, EXPENDITURE_TYPES)) {
            LocalDate date = ((OffsetDateTime) row[0]).atZoneSameInstant(TimeZones.APP_ZONE).toLocalDate();
            BigDecimal amount = (BigDecimal) row[1];
            String unlabelledKey = ExpenditurePeriodTotal.UNLABELLED;
            labelWeeks.merge(ExpenditurePeriods.weekKey(date) + ":" + unlabelledKey, amount, BigDecimal::add);
            labelMonths.merge(ExpenditurePeriods.monthKey(date) + ":" + unlabelledKey, amount, BigDecimal::add);
        }

        weeks.forEach((week, total) -> saveRow(userId, ExpenditurePeriodTotal.PERIOD_WEEK, week, null, total));
        months.forEach((month, total) -> saveRow(userId, ExpenditurePeriodTotal.PERIOD_MONTH, month, null, total));
        labelWeeks.forEach((key, total) -> saveLabelRow(userId, ExpenditurePeriodTotal.PERIOD_WEEK, key, total));
        labelMonths.forEach((key, total) -> saveLabelRow(userId, ExpenditurePeriodTotal.PERIOD_MONTH, key, total));
    }

    private void saveLabelRow(UUID userId, String periodType, String periodLabelKey, BigDecimal total) {
        String periodKey = periodLabelKey.substring(0, periodLabelKey.lastIndexOf(':'));
        String label = periodLabelKey.substring(periodLabelKey.lastIndexOf(':') + 1);
        saveRow(userId, periodType, periodKey, label, total);
    }

    @Transactional
    public void clearUser(UUID userId) {
        periodRepository.deleteAllByUserId(userId);
    }

    // ── Read ──────────────────────────────────────────────────────────────

    public ExpenditureSummaryResponse getSummary() {
        return buildSummary(getCurrentUserId());
    }

    @Transactional(readOnly = true)
    public ExpenditureSummaryResponse getSummaryForUser(UUID userId) {
        return buildSummary(userId);
    }

    private ExpenditureSummaryResponse buildSummary(UUID userId) {
        Map<String, ExpenditurePeriods.Range> ranges = ExpenditurePeriods.all();
        ExpenditurePeriods.Range yesterday = ranges.get(ExpenditurePeriods.YESTERDAY);
        ExpenditurePeriods.Range today = ranges.get(ExpenditurePeriods.TODAY);

        OffsetDateTime yesterdayStart = yesterday.startDate().atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        OffsetDateTime todayStart = today.startDate().atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        OffsetDateTime todayEnd = today.endDate().plusDays(1).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        List<Object[]> dayTotalsList = periodRepository.sumDayTotals(userId, EXPENDITURE_TYPES, yesterdayStart, todayStart, todayEnd);
        Object[] dayTotals = dayTotalsList.isEmpty() ? new Object[]{null, null} : dayTotalsList.get(0);

        Map<String, BigDecimal> stored = new HashMap<>();
        Map<String, List<LabelPeriodTotal>> labelBreakdown = new HashMap<>();
        for (ExpenditurePeriodTotal row : periodRepository.findAllByUserId(userId)) {
            if (row.getLabelName() == null) {
                stored.put(row.getPeriodType() + ":" + row.getPeriodKey(), row.getTotal());
            } else {
                labelBreakdown.computeIfAbsent(row.getPeriodType() + ":" + row.getPeriodKey(), k -> new ArrayList<>())
                        .add(new LabelPeriodTotal(displayName(row.getLabelName()), row.getTotal()));
            }
        }

        List<Object[]> dayLabelTotalsList = periodRepository.sumDayTotalsByLabel(userId, EXPENDITURE_TYPES, yesterdayStart, todayStart, todayEnd);
        List<LabelPeriodTotal> yesterdayByLabel = new ArrayList<>();
        List<LabelPeriodTotal> todayByLabel = new ArrayList<>();
        for (Object[] row : dayLabelTotalsList) {
            String name = (String) row[0];
            addIfNonZero(yesterdayByLabel, displayName(name), (BigDecimal) row[1]);
            addIfNonZero(todayByLabel, displayName(name), (BigDecimal) row[2]);
        }
        Object[] unlabelled = periodRepository.sumDayTotalsUnlabelled(userId, EXPENDITURE_TYPES, yesterdayStart, todayStart, todayEnd)
                .stream().findFirst().orElse(null);
        if (unlabelled != null) {
            addIfNonZero(yesterdayByLabel, displayName(ExpenditurePeriodTotal.UNLABELLED), (BigDecimal) unlabelled[0]);
            addIfNonZero(todayByLabel, displayName(ExpenditurePeriodTotal.UNLABELLED), (BigDecimal) unlabelled[1]);
        }

        ExpenditureSummaryResponse response = new ExpenditureSummaryResponse();
        response.setYesterday(nvl(dayTotals[0]));
        response.setToday(nvl(dayTotals[1]));
        response.setLastWeek(stored.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_WEEK, ranges.get(ExpenditurePeriods.LAST_WEEK)), BigDecimal.ZERO));
        response.setThisWeek(stored.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_WEEK, ranges.get(ExpenditurePeriods.THIS_WEEK)), BigDecimal.ZERO));
        response.setLastMonth(stored.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_MONTH, ranges.get(ExpenditurePeriods.LAST_MONTH)), BigDecimal.ZERO));
        response.setThisMonth(stored.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_MONTH, ranges.get(ExpenditurePeriods.THIS_MONTH)), BigDecimal.ZERO));
        response.setYesterdayByLabel(sortedDesc(yesterdayByLabel));
        response.setTodayByLabel(sortedDesc(todayByLabel));
        response.setLastWeekByLabel(sortedDesc(labelBreakdown.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_WEEK, ranges.get(ExpenditurePeriods.LAST_WEEK)), new ArrayList<>())));
        response.setThisWeekByLabel(sortedDesc(labelBreakdown.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_WEEK, ranges.get(ExpenditurePeriods.THIS_WEEK)), new ArrayList<>())));
        response.setLastMonthByLabel(sortedDesc(labelBreakdown.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_MONTH, ranges.get(ExpenditurePeriods.LAST_MONTH)), new ArrayList<>())));
        response.setThisMonthByLabel(sortedDesc(labelBreakdown.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_MONTH, ranges.get(ExpenditurePeriods.THIS_MONTH)), new ArrayList<>())));
        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean isExpenditure(TransactionType type) {
        return type == TransactionType.EXPENSE || type == TransactionType.LEND;
    }

    private void adjust(UUID userId, String periodType, String periodKey, String labelName, BigDecimal delta) {
        // Atomic DB-side upsert: concurrent adjustments for the same user+period
        // serialize on the row lock instead of racing through a read-modify-write.
        if (labelName == null) {
            periodRepository.adjustTotal(UuidCreator.getTimeOrderedEpoch(), userId, periodType, periodKey, delta);
            // Keep the table free of zero-total rows; readers already treat absent as ZERO.
            periodRepository.deleteZeroed(userId, periodType, periodKey);
        } else {
            periodRepository.adjustLabelTotal(UuidCreator.getTimeOrderedEpoch(), userId, periodType, periodKey, labelName, delta);
            periodRepository.deleteLabelZeroed(userId, periodType, periodKey, labelName);
        }
    }

    private void saveRow(UUID userId, String periodType, String periodKey, String labelName, BigDecimal total) {
        ExpenditurePeriodTotal row = new ExpenditurePeriodTotal();
        row.setUserId(userId);
        row.setPeriodType(periodType);
        row.setPeriodKey(periodKey);
        row.setLabelName(labelName);
        row.setTotal(total);
        periodRepository.save(row);
    }

    private String storedKey(String periodType, ExpenditurePeriods.Range range) {
        String key = periodType.equals(ExpenditurePeriodTotal.PERIOD_WEEK)
                ? ExpenditurePeriods.weekKey(range.startDate())
                : ExpenditurePeriods.monthKey(range.startDate());
        return periodType + ":" + key;
    }

    private BigDecimal nvl(Object value) {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }

    private void addIfNonZero(List<LabelPeriodTotal> into, String name, BigDecimal amount) {
        if (amount != null && amount.signum() != 0) {
            into.add(new LabelPeriodTotal(name, amount));
        }
    }

    private String displayName(String storedName) {
        return ExpenditurePeriodTotal.UNLABELLED.equals(storedName) ? "Unlabelled" : storedName;
    }

    private List<LabelPeriodTotal> sortedDesc(List<LabelPeriodTotal> breakdown) {
        List<LabelPeriodTotal> copy = new ArrayList<>(breakdown);
        copy.sort(Comparator.comparing(LabelPeriodTotal::amount).reversed());
        return copy;
    }

    /** Applies an overall/per-label adjustment pair for a transaction write. */
    private class Adjuster {
        private final UUID userId;
        private final LocalDate date;
        private final BigDecimal delta;
        private final Set<String> labelNames;

        Adjuster(UUID userId, LocalDate date, BigDecimal delta) {
            this(userId, date, delta, null);
        }

        Adjuster(UUID userId, LocalDate date, BigDecimal delta, Set<String> labelNames) {
            this.userId = userId;
            this.date = date;
            this.delta = delta;
            this.labelNames = labelNames;
        }

        void adjust() {
            if (labelNames == null) {
                ExpenditureSummaryService.this.adjust(userId, ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(date), null, delta);
                ExpenditureSummaryService.this.adjust(userId, ExpenditurePeriodTotal.PERIOD_MONTH, ExpenditurePeriods.monthKey(date), null, delta);
                return;
            }
            if (labelNames.isEmpty()) {
                ExpenditureSummaryService.this.adjust(userId, ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(date), ExpenditurePeriodTotal.UNLABELLED, delta);
                ExpenditureSummaryService.this.adjust(userId, ExpenditurePeriodTotal.PERIOD_MONTH, ExpenditurePeriods.monthKey(date), ExpenditurePeriodTotal.UNLABELLED, delta);
                return;
            }
            for (String name : labelNames) {
                ExpenditureSummaryService.this.adjust(userId, ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(date), name, delta);
                ExpenditureSummaryService.this.adjust(userId, ExpenditurePeriodTotal.PERIOD_MONTH, ExpenditurePeriods.monthKey(date), name, delta);
            }
        }
    }
}
