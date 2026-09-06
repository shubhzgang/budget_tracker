package com.budget.tracker.repository;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.ExpenditurePeriodTotal;
import com.budget.tracker.model.Label;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.util.ExpenditurePeriods;
import com.budget.tracker.util.TimeZones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DataJpaTest
@ActiveProfiles("test")
class ExpenditurePeriodTotalRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private ExpenditurePeriodTotalRepository periodRepository;

    @Autowired
    private Environment environment;

    private UUID userId;
    private Account account;

    private void assumePostgres() {
        String url = environment.getProperty("spring.datasource.url", "");
        assumeTrue(url.startsWith("jdbc:postgresql"),
                "adjustTotal uses PostgreSQL ON CONFLICT, unsupported by H2; skipped on: " + url);
    }

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        account = new Account();
        account.setName("Test Account");
        account.setType(AccountType.CASH);
        account.setInitialBalance(BigDecimal.valueOf(1000));
        account.setBalance(BigDecimal.valueOf(1000));
        account.setUserId(userId);
        accountRepository.save(account);
    }

    private Transaction expenseAt(OffsetDateTime date, String amount, TransactionType type) {
        Transaction t = new Transaction();
        t.setAmount(new BigDecimal(amount));
        t.setTransactionDate(date);
        t.setDescription("t-" + date);
        t.setType(type);
        t.setAccount(account);
        t.setUserId(userId);
        return t;
    }

    private Label savedLabel(String name) {
        Label label = new Label();
        label.setName(name);
        label.setUserId(userId);
        return labelRepository.save(label);
    }

    private void labelTransaction(Transaction t, Label label) {
        t.getLabels().add(label);
    }

    @Test
    void sumDayTotals_usesExclusiveEndBoundaries() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime todayStart = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        OffsetDateTime todayEnd = today.plusDays(1).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        OffsetDateTime yesterdayStart = today.minusDays(1).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        transactionRepository.save(expenseAt(todayStart, "10.00", TransactionType.EXPENSE));          // today (inclusive start)
        transactionRepository.save(expenseAt(todayStart.minusSeconds(1), "20.00", TransactionType.EXPENSE)); // yesterday
        transactionRepository.save(expenseAt(todayEnd.minus(1, java.time.temporal.ChronoUnit.MICROS), "30.00", TransactionType.LEND)); // today (just before end)
        transactionRepository.save(expenseAt(todayEnd, "40.00", TransactionType.EXPENSE));            // tomorrow - excluded
        transactionRepository.save(expenseAt(todayStart, "50.00", TransactionType.INCOME));           // not expenditure

        Object[] totals = periodRepository.sumDayTotals(userId, List.of(TransactionType.EXPENSE, TransactionType.LEND),
                yesterdayStart, todayStart, todayEnd).get(0);

        assertThat(((BigDecimal) totals[0])).isEqualByComparingTo("20.00");
        assertThat(((BigDecimal) totals[1])).isEqualByComparingTo("40.00");
    }

    @Test
    void sumDayTotals_isScopedToUser() {
        UUID otherUserId = UUID.randomUUID();
        Account otherAccount = new Account();
        otherAccount.setName("Other");
        otherAccount.setType(AccountType.CASH);
        otherAccount.setInitialBalance(BigDecimal.valueOf(100));
        otherAccount.setBalance(BigDecimal.valueOf(100));
        otherAccount.setUserId(otherUserId);
        accountRepository.save(otherAccount);

        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime todayStart = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        Transaction other = new Transaction();
        other.setAmount(new BigDecimal("99.00"));
        other.setTransactionDate(todayStart);
        other.setDescription("other");
        other.setType(TransactionType.EXPENSE);
        other.setAccount(otherAccount);
        other.setUserId(otherUserId);
        transactionRepository.save(other);

        transactionRepository.save(expenseAt(todayStart, "10.00", TransactionType.EXPENSE));

        Object[] totals = periodRepository.sumDayTotals(userId, List.of(TransactionType.EXPENSE, TransactionType.LEND),
                todayStart.minusDays(1), todayStart, todayStart.plusDays(1)).get(0);

        assertThat(((BigDecimal) totals[1])).isEqualByComparingTo("10.00");
    }

    @Test
    void sumDayTotals_emptyReturnsNulls() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime todayStart = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        Object[] totals = periodRepository.sumDayTotals(userId, List.of(TransactionType.EXPENSE, TransactionType.LEND),
                todayStart.minusDays(1), todayStart, todayStart.plusDays(1)).get(0);

        assertThat(totals[0]).isNull();
        assertThat(totals[1]).isNull();
    }

    @Test
    void findExpenditureDateAmounts_returnsOnlyExpenditureTypes() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime date = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        transactionRepository.save(expenseAt(date, "10.00", TransactionType.EXPENSE));
        transactionRepository.save(expenseAt(date, "20.00", TransactionType.LEND));
        transactionRepository.save(expenseAt(date, "30.00", TransactionType.INCOME));
        transactionRepository.save(expenseAt(date, "40.00", TransactionType.BORROW));

        List<Object[]> rows = periodRepository.findExpenditureDateAmounts(userId, List.of(TransactionType.EXPENSE, TransactionType.LEND));

        assertThat(rows).hasSize(2);
        BigDecimal sum = rows.stream().map(r -> (BigDecimal) r[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("30.00");
    }

    @Test
    void storedPeriodRows_roundTripWithUniqueKey() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        ExpenditurePeriodTotal week = new ExpenditurePeriodTotal();
        week.setUserId(userId);
        week.setPeriodType(ExpenditurePeriodTotal.PERIOD_WEEK);
        week.setPeriodKey(ExpenditurePeriods.weekKey(today));
        week.setTotal(new BigDecimal("12.50"));
        periodRepository.save(week);

        ExpenditurePeriodTotal found = periodRepository
                .findByUserIdAndPeriodTypeAndPeriodKey(userId, ExpenditurePeriodTotal.PERIOD_WEEK, ExpenditurePeriods.weekKey(today))
                .orElseThrow();

        assertThat(found.getTotal()).isEqualByComparingTo("12.50");
    }

    @Test
    void adjustTotal_upsertsAndAccumulates() {
        assumePostgres();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);
        String monthKey = ExpenditurePeriods.monthKey(today);

        periodRepository.adjustTotal(UUID.randomUUID(), userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, new BigDecimal("30.00"));
        periodRepository.adjustTotal(UUID.randomUUID(), userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, new BigDecimal("12.50"));
        periodRepository.adjustTotal(UUID.randomUUID(), userId, ExpenditurePeriodTotal.PERIOD_MONTH, monthKey, new BigDecimal("7.25"));

        assertThat(periodRepository.findByUserIdAndPeriodTypeAndPeriodKey(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey))
                .hasValueSatisfying(row -> assertThat(row.getTotal()).isEqualByComparingTo("42.50"));
        assertThat(periodRepository.findByUserIdAndPeriodTypeAndPeriodKey(userId, ExpenditurePeriodTotal.PERIOD_MONTH, monthKey))
                .hasValueSatisfying(row -> assertThat(row.getTotal()).isEqualByComparingTo("7.25"));
        assertThat(periodRepository.findAllByUserId(userId)).hasSize(2);
    }

    @Test
    void deleteZeroed_removesOnlyMatchingZeroRow() {
        assumePostgres();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);

        periodRepository.adjustTotal(UUID.randomUUID(), userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, new BigDecimal("10.00"));

        periodRepository.deleteZeroed(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey);
        assertThat(periodRepository.findByUserIdAndPeriodTypeAndPeriodKey(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey)).isPresent();

        periodRepository.adjustTotal(UUID.randomUUID(), userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, new BigDecimal("-10.00"));
        periodRepository.deleteZeroed(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey);
        assertThat(periodRepository.findByUserIdAndPeriodTypeAndPeriodKey(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey)).isEmpty();
    }

    @Test
    void adjustLabelTotal_upsertsAndAccumulatesPerLabel() {
        assumePostgres();
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);
        String monthKey = ExpenditurePeriods.monthKey(today);

        periodRepository.adjustLabelTotal(UUID.randomUUID(), userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, "NEEDS", new BigDecimal("30.00"));
        periodRepository.adjustLabelTotal(UUID.randomUUID(), userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, "NEEDS", new BigDecimal("12.50"));
        periodRepository.adjustLabelTotal(UUID.randomUUID(), userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, "WANTS", new BigDecimal("6.00"));
        periodRepository.adjustLabelTotal(UUID.randomUUID(), userId, ExpenditurePeriodTotal.PERIOD_MONTH, monthKey, "NEEDS", new BigDecimal("7.25"));

        List<ExpenditurePeriodTotal> rows = periodRepository.findAllByUserId(userId);
        assertThat(rows).hasSize(3);
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getLabelName()).isEqualTo("NEEDS");
            assertThat(r.getPeriodType()).isEqualTo(ExpenditurePeriodTotal.PERIOD_WEEK);
            assertThat(r.getTotal()).isEqualByComparingTo("42.50");
        });
    }

    @Test
    void deleteLabelZeroed_removesOnlyMatchingLabelZeroRow() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        String weekKey = ExpenditurePeriods.weekKey(today);

        ExpenditurePeriodTotal zeroRow = new ExpenditurePeriodTotal();
        zeroRow.setUserId(userId);
        zeroRow.setPeriodType(ExpenditurePeriodTotal.PERIOD_WEEK);
        zeroRow.setPeriodKey(weekKey);
        zeroRow.setLabelName("NEEDS");
        zeroRow.setTotal(BigDecimal.ZERO);
        periodRepository.save(zeroRow);

        periodRepository.deleteLabelZeroed(userId, ExpenditurePeriodTotal.PERIOD_WEEK, weekKey, "NEEDS");

        assertThat(periodRepository.findAllByUserId(userId)).isEmpty();
    }

    @Test
    void sumDayTotalsByLabel_returnsPerLabelDayTotals() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime todayStart = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        OffsetDateTime todayEnd = today.plusDays(1).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();
        OffsetDateTime yesterdayStart = today.minusDays(1).atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        Label needs = savedLabel("NEEDS");
        Label wants = savedLabel("WANTS");

        Transaction t1 = expenseAt(todayStart, "10.00", TransactionType.EXPENSE);
        labelTransaction(t1, needs);
        transactionRepository.save(t1);

        Transaction t2 = expenseAt(todayStart, "20.00", TransactionType.EXPENSE);
        labelTransaction(t2, wants);
        transactionRepository.save(t2);

        Transaction t3 = expenseAt(yesterdayStart, "5.00", TransactionType.EXPENSE);
        labelTransaction(t3, needs);
        transactionRepository.save(t3);

        List<Object[]> rows = periodRepository.sumDayTotalsByLabel(userId, List.of(TransactionType.EXPENSE, TransactionType.LEND),
                yesterdayStart, todayStart, todayEnd);

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(r -> {
            assertThat(r[0]).isEqualTo("NEEDS");
            assertThat(((BigDecimal) r[1])).isEqualByComparingTo("5.00");
            assertThat(((BigDecimal) r[2])).isEqualByComparingTo("10.00");
        });
        assertThat(rows).anySatisfy(r -> {
            assertThat(r[0]).isEqualTo("WANTS");
            assertThat(((BigDecimal) r[2])).isEqualByComparingTo("20.00");
        });
    }

    @Test
    void sumDayTotalsUnlabelled_returnsOnlyUnlabelled() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime todayStart = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        Label needs = savedLabel("NEEDS");
        Transaction labelled = expenseAt(todayStart, "10.00", TransactionType.EXPENSE);
        labelTransaction(labelled, needs);
        transactionRepository.save(labelled);

        transactionRepository.save(expenseAt(todayStart, "25.00", TransactionType.EXPENSE));

        Object[] totals = periodRepository.sumDayTotalsUnlabelled(userId, List.of(TransactionType.EXPENSE, TransactionType.LEND),
                todayStart.minusDays(1), todayStart, todayStart.plusDays(1)).get(0);

        assertThat(((BigDecimal) totals[1])).isEqualByComparingTo("25.00");
    }

    @Test
    void findExpenditureDateAmountsWithLabels_returnsLabelNamePerRow() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime date = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        Label needs = savedLabel("NEEDS");
        Label wants = savedLabel("WANTS");
        Transaction t = expenseAt(date, "10.00", TransactionType.EXPENSE);
        labelTransaction(t, needs);
        labelTransaction(t, wants);
        transactionRepository.save(t);

        List<Object[]> rows = periodRepository.findExpenditureDateAmountsWithLabels(userId, List.of(TransactionType.EXPENSE, TransactionType.LEND));

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(r -> assertThat(r[2]).isEqualTo("NEEDS"));
        assertThat(rows).anySatisfy(r -> assertThat(r[2]).isEqualTo("WANTS"));
    }

    @Test
    void findExpenditureDateAmountsUnlabelled_omitsLabelled() {
        LocalDate today = LocalDate.now(TimeZones.APP_ZONE);
        OffsetDateTime date = today.atStartOfDay(TimeZones.APP_ZONE).toOffsetDateTime();

        Label needs = savedLabel("NEEDS");
        Transaction labelled = expenseAt(date, "10.00", TransactionType.EXPENSE);
        labelTransaction(labelled, needs);
        transactionRepository.save(labelled);
        transactionRepository.save(expenseAt(date, "25.00", TransactionType.EXPENSE));

        List<Object[]> rows = periodRepository.findExpenditureDateAmountsUnlabelled(userId, List.of(TransactionType.EXPENSE, TransactionType.LEND));

        assertThat(rows).hasSize(1);
        assertThat(((BigDecimal) rows.get(0)[1])).isEqualByComparingTo("25.00");
    }
}
