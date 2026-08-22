package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.ExpenditurePeriodTotal;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.payload.response.ExpenditureSummaryResponse;
import com.budget.tracker.repository.ExpenditurePeriodTotalRepository;
import com.budget.tracker.util.ExpenditurePeriods;
import com.budget.tracker.util.TimeZones;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public void recordExpenditure(UUID userId, OffsetDateTime transactionDate, TransactionType type, BigDecimal amount) {
        if (!isExpenditure(type)) return;
        LocalDate date = transactionDate.atZoneSameInstant(TimeZones.APP_ZONE).toLocalDate();
        adjust(userId, ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(date), amount);
        adjust(userId, ExpenditurePeriodTotal.PERIOD_MONTH, ExpenditurePeriods.monthKey(date), amount);
    }

    @Transactional
    public void removeExpenditure(UUID userId, OffsetDateTime transactionDate, TransactionType type, BigDecimal amount) {
        if (!isExpenditure(type)) return;
        LocalDate date = transactionDate.atZoneSameInstant(TimeZones.APP_ZONE).toLocalDate();
        adjust(userId, ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(date), amount.negate());
        adjust(userId, ExpenditurePeriodTotal.PERIOD_MONTH, ExpenditurePeriods.monthKey(date), amount.negate());
    }

    /** Rebuild a user's stored week/month totals from their transactions (bulk loads, backfill). */
    @Transactional
    public void recomputeForUser(UUID userId) {
        periodRepository.deleteAllByUserId(userId);
        Map<String, BigDecimal> weeks = new HashMap<>();
        Map<String, BigDecimal> months = new HashMap<>();
        for (Object[] row : periodRepository.findExpenditureDateAmounts(userId, EXPENDITURE_TYPES)) {
            LocalDate date = ((OffsetDateTime) row[0]).atZoneSameInstant(TimeZones.APP_ZONE).toLocalDate();
            BigDecimal amount = (BigDecimal) row[1];
            weeks.merge(ExpenditurePeriods.weekKey(date), amount, BigDecimal::add);
            months.merge(ExpenditurePeriods.monthKey(date), amount, BigDecimal::add);
        }
        weeks.forEach((key, total) -> saveRow(userId, ExpenditurePeriodTotal.PERIOD_WEEK, key, total));
        months.forEach((key, total) -> saveRow(userId, ExpenditurePeriodTotal.PERIOD_MONTH, key, total));
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
        for (ExpenditurePeriodTotal row : periodRepository.findAllByUserId(userId)) {
            stored.put(row.getPeriodType() + ":" + row.getPeriodKey(), row.getTotal());
        }

        ExpenditureSummaryResponse response = new ExpenditureSummaryResponse();
        response.setYesterday(nvl(dayTotals[0]));
        response.setToday(nvl(dayTotals[1]));
        response.setLastWeek(stored.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_WEEK, ranges.get(ExpenditurePeriods.LAST_WEEK)), BigDecimal.ZERO));
        response.setThisWeek(stored.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_WEEK, ranges.get(ExpenditurePeriods.THIS_WEEK)), BigDecimal.ZERO));
        response.setLastMonth(stored.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_MONTH, ranges.get(ExpenditurePeriods.LAST_MONTH)), BigDecimal.ZERO));
        response.setThisMonth(stored.getOrDefault(storedKey(ExpenditurePeriodTotal.PERIOD_MONTH, ranges.get(ExpenditurePeriods.THIS_MONTH)), BigDecimal.ZERO));
        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean isExpenditure(TransactionType type) {
        return type == TransactionType.EXPENSE || type == TransactionType.LEND;
    }

    private void adjust(UUID userId, String periodType, String periodKey, BigDecimal delta) {
        // Atomic DB-side upsert: concurrent adjustments for the same user+period
        // serialize on the row lock instead of racing through a read-modify-write.
        periodRepository.adjustTotal(UuidCreator.getTimeOrderedEpoch(), userId, periodType, periodKey, delta);
        // Keep the table free of zero-total rows; readers already treat absent as ZERO.
        periodRepository.deleteZeroed(userId, periodType, periodKey);
    }

    private void saveRow(UUID userId, String periodType, String periodKey, BigDecimal total) {
        ExpenditurePeriodTotal row = new ExpenditurePeriodTotal();
        row.setUserId(userId);
        row.setPeriodType(periodType);
        row.setPeriodKey(periodKey);
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
}
