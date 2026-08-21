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

    // -- recordExpenditure --

    @Test
    void recordExpenditure_expense_savesWeekAndMonthRows() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        service.recordExpenditure(userId, date, TransactionType.EXPENSE, new BigDecimal("42.00"));

        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        verify(periodRepository, org.mockito.Mockito.times(2)).save(any(ExpenditurePeriodTotal.class));
        verify(periodRepository).save(org.mockito.ArgumentMatchers.argThat(r ->
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_WEEK)
                        && r.getPeriodKey().equals(ExpenditurePeriods.weekKey(today))
                        && r.getTotal().compareTo(new BigDecimal("42.00")) == 0));
        verify(periodRepository).save(org.mockito.ArgumentMatchers.argThat(r ->
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_MONTH)
                        && r.getPeriodKey().equals(ExpenditurePeriods.monthKey(today))
                        && r.getTotal().compareTo(new BigDecimal("42.00")) == 0));
    }

    @Test
    void recordExpenditure_income_isIgnored() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        service.recordExpenditure(userId, date, TransactionType.INCOME, new BigDecimal("42.00"));

        verify(periodRepository, never()).save(any(ExpenditurePeriodTotal.class));
        verify(periodRepository, never()).delete(any(ExpenditurePeriodTotal.class));
    }

    @Test
    void recordExpenditure_addsToExistingRow() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);
        when(periodRepository.findByUserIdAndPeriodTypeAndPeriodKey(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey))
                .thenReturn(java.util.Optional.of(row(ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, "10.00")));
        when(periodRepository.findByUserIdAndPeriodTypeAndPeriodKey(userId, ExpenditurePeriodTotal.PERIOD_MONTH, ExpenditurePeriods.monthKey(today)))
                .thenReturn(java.util.Optional.empty());

        service.recordExpenditure(userId, date, TransactionType.LEND, new BigDecimal("5.00"));

        verify(periodRepository).save(org.mockito.ArgumentMatchers.argThat(r ->
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_WEEK)
                        && r.getTotal().compareTo(new BigDecimal("15.00")) == 0));
    }

    // -- removeExpenditure --

    @Test
    void removeExpenditure_deletesRowWhenTotalReachesZero() {
        OffsetDateTime date = LocalDate.now(TimeZones.APP_ZONE).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);
        String monthKey = ExpenditurePeriods.monthKey(today);
        when(periodRepository.findByUserIdAndPeriodTypeAndPeriodKey(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey))
                .thenReturn(java.util.Optional.of(row(ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, "25.00")));
        when(periodRepository.findByUserIdAndPeriodTypeAndPeriodKey(userId, ExpenditurePeriodTotal.PERIOD_MONTH, monthKey))
                .thenReturn(java.util.Optional.of(row(ExpenditurePeriodTotal.PERIOD_MONTH, monthKey, "100.00")));

        service.removeExpenditure(userId, date, TransactionType.EXPENSE, new BigDecimal("25.00"));

        verify(periodRepository).delete(org.mockito.ArgumentMatchers.argThat(r ->
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_WEEK) && r.getPeriodKey().equals(weekKey)));
        verify(periodRepository).save(org.mockito.ArgumentMatchers.argThat(r ->
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_MONTH)
                        && r.getTotal().compareTo(new BigDecimal("75.00")) == 0));
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
                r.getPeriodType().equals(ExpenditurePeriodTotal.PERIOD_MONTH)));
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
    }
}
