package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.ExpenditurePeriodTotal;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.payload.response.ExpenditureSummaryResponse;
import com.budget.tracker.repository.ExpenditurePeriodTotalRepository;
import com.budget.tracker.util.ExpenditurePeriods;
import com.budget.tracker.util.TimeZones;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenditureSummaryServiceTest {

    @Mock
    private ExpenditurePeriodTotalRepository periodRepository;

    private ExpenditureSummaryService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ExpenditureSummaryService(periodRepository);
        userId = UUID.randomUUID();
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private ExpenditurePeriodTotal row(String type, String key, String total) {
        ExpenditurePeriodTotal r = new ExpenditurePeriodTotal();
        r.setUserId(userId);
        r.setPeriodType(type);
        r.setPeriodKey(key);
        r.setTotal(new BigDecimal(total));
        return r;
    }

    private ExpenditurePeriodTotal labelRow(String type, String key, String labelName, String total) {
        ExpenditurePeriodTotal r = new ExpenditurePeriodTotal();
        r.setUserId(userId);
        r.setPeriodType(type);
        r.setPeriodKey(key);
        r.setLabelName(labelName);
        r.setTotal(new BigDecimal(total));
        return r;
    }

    // -- recordExpenditure --

    @Test
    void recordExpenditure_expense_upsertsWeekAndMonthRows() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        service.recordExpenditure(userId, date, TransactionType.EXPENSE, new BigDecimal("42.00"), Set.of());

        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        verify(periodRepository).adjustTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_WEEK), eq(ExpenditurePeriods.weekKey(today)),
                eq(new BigDecimal("42.00")));
        verify(periodRepository).adjustTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_MONTH), eq(ExpenditurePeriods.monthKey(today)),
                eq(new BigDecimal("42.00")));
    }

    @Test
    void recordExpenditure_income_isIgnored() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        service.recordExpenditure(userId, date, TransactionType.INCOME, new BigDecimal("42.00"), Set.of());

        verify(periodRepository, never()).adjustTotal(any(UUID.class), any(UUID.class), any(), any(), any());
        verify(periodRepository, never()).adjustLabelTotal(any(UUID.class), any(UUID.class), any(), any(), any(), any());
        verify(periodRepository, never()).deleteZeroed(any(UUID.class), any(), any());
        verify(periodRepository, never()).deleteLabelZeroed(any(UUID.class), any(), any(), any());
    }

    @Test
    void recordExpenditure_lend_passesPositiveDeltaPerPeriodKey() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);

        service.recordExpenditure(userId, date, TransactionType.LEND, new BigDecimal("5.00"), Set.of());

        verify(periodRepository).adjustTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_WEEK), eq(ExpenditurePeriods.weekKey(today)),
                eq(new BigDecimal("5.00")));
        verify(periodRepository).adjustTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_MONTH), eq(ExpenditurePeriods.monthKey(today)),
                eq(new BigDecimal("5.00")));
    }

    // -- recordExpenditure (per-label) --

    @Test
    void recordExpenditure_withLabels_adjustsPerLabelRows() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);
        String monthKey = ExpenditurePeriods.monthKey(today);
        Set<String> labelNames = Set.of("NEEDS", "WANTS");

        service.recordExpenditure(userId, date, TransactionType.EXPENSE, new BigDecimal("10.00"), labelNames);

        verify(periodRepository).adjustLabelTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_WEEK), eq(weekKey), eq("NEEDS"), eq(new BigDecimal("10.00")));
        verify(periodRepository).adjustLabelTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_WEEK), eq(weekKey), eq("WANTS"), eq(new BigDecimal("10.00")));
        verify(periodRepository).adjustLabelTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_MONTH), eq(monthKey), eq("NEEDS"), eq(new BigDecimal("10.00")));
        verify(periodRepository).adjustLabelTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_MONTH), eq(monthKey), eq("WANTS"), eq(new BigDecimal("10.00")));
    }

    @Test
    void recordExpenditure_withNoLabels_adjustsUnlabelledRow() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);
        String monthKey = ExpenditurePeriods.monthKey(today);

        service.recordExpenditure(userId, date, TransactionType.EXPENSE, new BigDecimal("10.00"), Set.of());

        verify(periodRepository).adjustLabelTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_WEEK), eq(weekKey), eq(ExpenditurePeriodTotal.UNLABELLED), eq(new BigDecimal("10.00")));
        verify(periodRepository).adjustLabelTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_MONTH), eq(monthKey), eq(ExpenditurePeriodTotal.UNLABELLED), eq(new BigDecimal("10.00")));
    }

    // -- removeExpenditure --

    @Test
    void removeExpenditure_appliesNegatedDeltaAndCleansUpZeroRows() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);
        String monthKey = ExpenditurePeriods.monthKey(today);

        service.removeExpenditure(userId, date, TransactionType.EXPENSE, new BigDecimal("25.00"), Set.of("NEEDS"));

        verify(periodRepository).adjustTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_WEEK), eq(weekKey), eq(new BigDecimal("-25.00")));
        verify(periodRepository).adjustTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_MONTH), eq(monthKey), eq(new BigDecimal("-25.00")));
        verify(periodRepository).deleteZeroed(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey);
        verify(periodRepository).deleteZeroed(userId, ExpenditurePeriodTotal.PERIOD_MONTH, monthKey);
    }

    @Test
    void removeExpenditure_withLabels_revertsPerLabelRows() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);
        String monthKey = ExpenditurePeriods.monthKey(today);

        service.removeExpenditure(userId, date, TransactionType.EXPENSE, new BigDecimal("25.00"), Set.of("NEEDS"));

        verify(periodRepository).adjustLabelTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_WEEK), eq(weekKey), eq("NEEDS"), eq(new BigDecimal("-25.00")));
        verify(periodRepository).adjustLabelTotal(any(UUID.class), eq(userId),
                eq(ExpenditurePeriodTotal.PERIOD_MONTH), eq(monthKey), eq("NEEDS"), eq(new BigDecimal("-25.00")));
        verify(periodRepository).deleteLabelZeroed(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, "NEEDS");
        verify(periodRepository).deleteLabelZeroed(userId, ExpenditurePeriodTotal.PERIOD_MONTH, monthKey, "NEEDS");
    }

    // -- recomputeForUser --

    @Test
    void recomputeForUser_rebuildsRowsFromTransactions() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime thisWeek = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        OffsetDateTime lastWeek = today.minusWeeks(1).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        when(periodRepository.findExpenditureDateAmounts(eq(userId), any()))
                .thenReturn(List.of(
                        new Object[]{thisWeek, new BigDecimal("30.00")},
                        new Object[]{thisWeek, new BigDecimal("70.00")},
                        new Object[]{lastWeek, new BigDecimal("10.00")}
                ));

        service.recomputeForUser(userId);

        verify(periodRepository).deleteAllByUserId(userId);
        verify(periodRepository).save(org.mockito.ArgumentMatchers.argThat(r ->
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_WEEK)
                        && r.getPeriodKey().equals(ExpenditurePeriods.weekKey(today))
                        && r.getTotal().compareTo(new BigDecimal("100.00")) == 0));
        verify(periodRepository).save(org.mockito.ArgumentMatchers.argThat(r ->
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_WEEK)
                        && r.getPeriodKey().equals(ExpenditurePeriods.weekKey(today.minusWeeks(1)))
                        && r.getTotal().compareTo(new BigDecimal("10.00")) == 0));
        verify(periodRepository).save(org.mockito.ArgumentMatchers.argThat(r ->
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_MONTH)
                        && r.getPeriodKey().equals(ExpenditurePeriods.monthKey(today))));
    }

    @Test
    void clearUser_deletesAllRows() {
        service.clearUser(userId);
        verify(periodRepository).deleteAllByUserId(userId);
    }

    // -- getSummary --

    @Test
    void getSummary_combinesDayTotalsAndStoredPeriods() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        ExpenditurePeriods.Range thisWeek = ExpenditurePeriods.all().get(ExpenditurePeriods.THIS_WEEK);
        ExpenditurePeriods.Range lastWeek = ExpenditurePeriods.all().get(ExpenditurePeriods.LAST_WEEK);
        ExpenditurePeriods.Range thisMonth = ExpenditurePeriods.all().get(ExpenditurePeriods.THIS_MONTH);
        ExpenditurePeriods.Range lastMonth = ExpenditurePeriods.all().get(ExpenditurePeriods.LAST_MONTH);

        when(periodRepository.sumDayTotals(eq(userId), any(), any(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{new BigDecimal("1.50"), new BigDecimal("2.50")}));
        when(periodRepository.findAllByUserId(userId)).thenReturn(List.of(
                row(ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(thisWeek.startDate()), "300.00"),
                row(ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(lastWeek.startDate()), "200.00"),
                row(ExpenditurePeriodTotal.PERIOD_MONTH, ExpenditurePeriods.monthKey(thisMonth.startDate()), "900.00"),
                row(ExpenditurePeriodTotal.PERIOD_MONTH, ExpenditurePeriods.monthKey(lastMonth.startDate()), "800.00")
        ));

        ExpenditureSummaryResponse summary = service.getSummary();

        assertEquals(0, summary.getYesterday().compareTo(new BigDecimal("1.50")));
        assertEquals(0, summary.getToday().compareTo(new BigDecimal("2.50")));
        assertEquals(0, summary.getThisWeek().compareTo(new BigDecimal("300.00")));
        assertEquals(0, summary.getLastWeek().compareTo(new BigDecimal("200.00")));
        assertEquals(0, summary.getThisMonth().compareTo(new BigDecimal("900.00")));
        assertEquals(0, summary.getLastMonth().compareTo(new BigDecimal("800.00")));
    }

    @Test
    void getSummary_missingRowsYieldZero() {
        when(periodRepository.sumDayTotals(eq(userId), any(), any(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{null, null}));
        when(periodRepository.findAllByUserId(userId)).thenReturn(List.of());

        ExpenditureSummaryResponse summary = service.getSummary();

        assertTrue(summary.getYesterday().signum() == 0);
        assertTrue(summary.getToday().signum() == 0);
        assertTrue(summary.getThisWeek().signum() == 0);
        assertTrue(summary.getLastWeek().signum() == 0);
        assertTrue(summary.getThisMonth().signum() == 0);
        assertTrue(summary.getLastMonth().signum() == 0);
        assertTrue(summary.getTodayByLabel().isEmpty());
        assertTrue(summary.getThisWeekByLabel().isEmpty());
    }

    @Test
    void getSummary_includesDayLabelBreakdownsSortedByAmount() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime yesterdayStart = today.minusDays(1).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        OffsetDateTime todayStart = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        OffsetDateTime todayEnd = today.plusDays(1).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        when(periodRepository.sumDayTotals(eq(userId), any(), any(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{null, null}));
        when(periodRepository.sumDayTotalsByLabel(eq(userId), any(), eq(yesterdayStart), eq(todayStart), eq(todayEnd)))
                .thenReturn(java.util.List.<Object[]>of(
                        new Object[]{"WANTS", new BigDecimal("5.00"), new BigDecimal("20.00")},
                        new Object[]{"NEEDS", new BigDecimal("10.00"), new BigDecimal("40.00")}
                ));
        when(periodRepository.findAllByUserId(userId)).thenReturn(List.of());

        ExpenditureSummaryResponse summary = service.getSummary();

        assertEquals("NEEDS", summary.getTodayByLabel().get(0).labelName());
        assertEquals(0, summary.getTodayByLabel().get(0).amount().compareTo(new BigDecimal("40.00")));
        assertEquals("WANTS", summary.getTodayByLabel().get(1).labelName());
        assertEquals("NEEDS", summary.getYesterdayByLabel().get(0).labelName());
    }

    @Test
    void getSummary_unlabelledDayTransactionsAppearInBreakdown() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        when(periodRepository.sumDayTotals(eq(userId), any(), any(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{null, null}));
        when(periodRepository.sumDayTotalsByLabel(eq(userId), any(), any(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of());
        when(periodRepository.sumDayTotalsUnlabelled(eq(userId), any(), any(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{new BigDecimal("3.00"), new BigDecimal("7.00")}));
        when(periodRepository.findAllByUserId(userId)).thenReturn(List.of());

        ExpenditureSummaryResponse summary = service.getSummary();

        assertEquals("Unlabelled", summary.getTodayByLabel().get(0).labelName());
        assertEquals(0, summary.getTodayByLabel().get(0).amount().compareTo(new BigDecimal("7.00")));
        assertEquals(0, summary.getYesterdayByLabel().get(0).amount().compareTo(new BigDecimal("3.00")));
    }

    @Test
    void getSummary_includesStoredPeriodLabelBreakdowns() {
        ExpenditurePeriods.Range thisWeek = ExpenditurePeriods.all().get(ExpenditurePeriods.THIS_WEEK);
        when(periodRepository.sumDayTotals(eq(userId), any(), any(), any(), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{null, null}));
        when(periodRepository.findAllByUserId(userId)).thenReturn(List.of(
                row(ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(thisWeek.startDate()), "300.00"),
                labelRow(ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(thisWeek.startDate()),
                        "NEEDS", "200.00"),
                labelRow(ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(thisWeek.startDate()),
                        "WANTS", "100.00")
        ));

        ExpenditureSummaryResponse summary = service.getSummary();

        assertTrue(summary.getThisWeekByLabel().size() == 2);
        assertEquals("NEEDS", summary.getThisWeekByLabel().get(0).labelName());
        assertEquals("WANTS", summary.getThisWeekByLabel().get(1).labelName());
    }

    // -- recomputeForUser (per-label) --

    @Test
    void recomputeForUser_rebuildsLabelAndUnlabelledRows() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime now = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        when(periodRepository.findExpenditureDateAmounts(eq(userId), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{now, new BigDecimal("100.00")}));
        when(periodRepository.findExpenditureDateAmountsWithLabels(eq(userId), any()))
                .thenReturn(List.of(
                        new Object[]{now, new BigDecimal("30.00"), "NEEDS"},
                        new Object[]{now, new BigDecimal("20.00"), "WANTS"}
                ));
        when(periodRepository.findExpenditureDateAmountsUnlabelled(eq(userId), any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{now, new BigDecimal("50.00")}));

        service.recomputeForUser(userId);

        verify(periodRepository).deleteAllByUserId(userId);
        verify(periodRepository).save(org.mockito.ArgumentMatchers.argThat(r -> {
            String weekKey = ExpenditurePeriods.weekKey(today);
            return r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_WEEK)
                    && r.getPeriodKey().equals(weekKey)
                    && r.getLabelName() != null
                    && r.getLabelName().equals("NEEDS")
                    && r.getTotal().compareTo(new BigDecimal("30.00")) == 0;
        }));
        verify(periodRepository).save(org.mockito.ArgumentMatchers.argThat(r ->
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_WEEK)
                        && r.getLabelName() != null
                        && r.getLabelName().equals(ExpenditurePeriodTotal.UNLABELLED)
                        && r.getTotal().compareTo(new BigDecimal("50.00")) == 0));
    }
}
