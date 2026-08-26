# Label-Wise Expenditure Breakdown on Dashboard

Add per-label (NEEDS, WANTS, SAVINGS, custom labels, and Unlabelled) expenditure breakdown under each period card on the Dashboard, in addition to the existing totals.

## Current Architecture

The dashboard shows 6 period cards (Yesterday, Today, Last Week, This Week, Last Month, This Month) with a single total amount each.

**Hybrid computation strategy:**
- **Today & Yesterday** — computed live from `transactions` table via `sumDayTotals()` query
- **Week & Month periods** — eagerly maintained in `expenditure_period_totals` table, updated on every transaction create/update/delete via atomic `ON CONFLICT DO UPDATE` upserts

**Labels** are user-defined entities in the `labels` table (defaults: NEEDS, WANTS, SAVINGS). Transactions have a many-to-many relationship with labels via `transaction_labels` join table. A transaction can have **0 or more** labels.

---

## Design Decisions (Resolved)

| Decision | Resolution |
|---|---|
| **Multi-label transactions** | A transaction tagged with multiple labels counts its full amount under each label. Label subtotals may sum to more than the period total. The total remains the true expenditure figure. |
| **Unlabelled transactions** | An **"Unlabelled"** row will be shown for transactions without any labels. |
| **Custom labels** | All labels that have expenditure in a given period are shown, not just the 3 defaults. |
| **Clicking label rows** | No per-label click filtering. Only the overall card total links to the transactions page filtered by date range (existing behavior). |
| **Card expansion** | Label breakdown is **always visible** — no toggle/expand needed. |
| **Ordering** | Labels in the breakdown are sorted by **amount (highest first)**. |

---

## Proposed Changes

### Database — Flyway Migration

#### [NEW] `src/main/resources/db/migration/V3__expenditure_period_totals_by_label.sql`

Add a `label_name` column to `expenditure_period_totals` to store per-label aggregates alongside the existing overall totals. The current rows (with `label_name = NULL`) continue to represent the overall total for the period.

```sql
-- Add nullable label_name column; NULL = overall total (existing rows).
ALTER TABLE expenditure_period_totals ADD COLUMN label_name VARCHAR(255);

-- Drop the old unique constraint and replace with one that includes label_name.
ALTER TABLE expenditure_period_totals
    DROP CONSTRAINT uq_expenditure_period_totals;

-- Use a unique index with COALESCE so that NULL label_name values are treated
-- as equal (PostgreSQL unique constraints treat NULLs as distinct).
CREATE UNIQUE INDEX uq_expenditure_period_totals
    ON expenditure_period_totals (user_id, period_type, period_key, COALESCE(label_name, ''));

-- Backfill per-label WEEK rows from existing transactions.
INSERT INTO expenditure_period_totals (id, user_id, period_type, period_key, label_name, total)
SELECT gen_random_uuid(), t.user_id, 'WEEK',
       TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'IYYY-"W"IW'),
       l.name,
       SUM(t.amount)
FROM transactions t
JOIN transaction_labels tl ON tl.transaction_id = t.id
JOIN labels l ON l.id = tl.label_id
WHERE t.type IN ('EXPENSE', 'LEND')
GROUP BY t.user_id, TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'IYYY-"W"IW'), l.name;

-- Backfill per-label MONTH rows.
INSERT INTO expenditure_period_totals (id, user_id, period_type, period_key, label_name, total)
SELECT gen_random_uuid(), t.user_id, 'MONTH',
       TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM'),
       l.name,
       SUM(t.amount)
FROM transactions t
JOIN transaction_labels tl ON tl.transaction_id = t.id
JOIN labels l ON l.id = tl.label_id
WHERE t.type IN ('EXPENSE', 'LEND')
GROUP BY t.user_id, TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM'), l.name;

-- Backfill "Unlabelled" WEEK rows for transactions with no labels.
INSERT INTO expenditure_period_totals (id, user_id, period_type, period_key, label_name, total)
SELECT gen_random_uuid(), t.user_id, 'WEEK',
       TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'IYYY-"W"IW'),
       '__UNLABELLED__',
       SUM(t.amount)
FROM transactions t
WHERE t.type IN ('EXPENSE', 'LEND')
  AND NOT EXISTS (SELECT 1 FROM transaction_labels tl WHERE tl.transaction_id = t.id)
GROUP BY t.user_id, TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'IYYY-"W"IW');

-- Backfill "Unlabelled" MONTH rows.
INSERT INTO expenditure_period_totals (id, user_id, period_type, period_key, label_name, total)
SELECT gen_random_uuid(), t.user_id, 'MONTH',
       TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM'),
       '__UNLABELLED__',
       SUM(t.amount)
FROM transactions t
WHERE t.type IN ('EXPENSE', 'LEND')
  AND NOT EXISTS (SELECT 1 FROM transaction_labels tl WHERE tl.transaction_id = t.id)
GROUP BY t.user_id, TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM');
```

**Design rationale — why `label_name` (denormalized string) instead of `label_id` (FK)?**
- Labels can be renamed or deleted. A `label_id` FK would break or cascade-delete historical aggregates. Storing the name at write-time is a snapshot, consistent with how we'd display it.
- This is an aggregate/cache table, not a source-of-truth. If a user renames "WANTS" to "DESIRES", old period rows retain "WANTS" and new ones get "DESIRES" — which is actually more accurate historically.
- Avoids expensive JOINs in the read path.
- The sentinel value `__UNLABELLED__` is used for transactions with no labels. This is distinct from any real label name (label names cannot contain pipes `|`, so a double-underscore sentinel is safe and visually distinct in the DB). On the frontend, `__UNLABELLED__` is rendered as "Unlabelled".

**Unique constraint note:** We use a unique index with `COALESCE(label_name, '')` instead of a constraint, because PostgreSQL unique constraints treat NULLs as distinct. The overall-total rows have `label_name = NULL` and must remain unique per `(user_id, period_type, period_key)`. The upsert SQL for overall totals will use `COALESCE(label_name, '')` in the conflict target.

---

### Model Layer

#### [MODIFY] `src/main/java/com/budget/tracker/model/ExpenditurePeriodTotal.java`

Add `labelName` field and a sentinel constant:

```diff
+    public static final String UNLABELLED = "__UNLABELLED__";
+
     @Column(name = "label_name")
+    private String labelName;
```

Update the `@UniqueConstraint` annotation to include `label_name`:

```diff
-@Table(name = "expenditure_period_totals",
-        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "period_type", "period_key"}))
+@Table(name = "expenditure_period_totals")
```

(The unique constraint is now managed by the migration as a unique index with `COALESCE`, not a JPA-managed constraint.)

---

### Repository Layer

#### [MODIFY] `src/main/java/com/budget/tracker/repository/ExpenditurePeriodTotalRepository.java`

1. **Update existing `adjustTotal`** — explicitly include `label_name` as NULL and use the new conflict target:
   ```sql
   INSERT INTO expenditure_period_totals (id, user_id, period_type, period_key, label_name, total)
   VALUES (:id, :userId, :periodType, :periodKey, NULL, :delta)
   ON CONFLICT (user_id, period_type, period_key, COALESCE(label_name, ''))
   DO UPDATE SET total = expenditure_period_totals.total + EXCLUDED.total
   ```

2. **New upsert for per-label adjustments** — `adjustLabelTotal(id, userId, periodType, periodKey, labelName, delta)`:
   ```sql
   INSERT INTO expenditure_period_totals (id, user_id, period_type, period_key, label_name, total)
   VALUES (:id, :userId, :periodType, :periodKey, :labelName, :delta)
   ON CONFLICT (user_id, period_type, period_key, COALESCE(label_name, ''))
   DO UPDATE SET total = expenditure_period_totals.total + EXCLUDED.total
   ```

3. **Update `deleteZeroed`** — the existing JPQL delete targets rows where `labelName IS NULL` (overall totals). Add a new method `deleteLabelZeroed(userId, periodType, periodKey, labelName)` for label-specific rows:
   ```java
   @Modifying
   @Query("DELETE FROM ExpenditurePeriodTotal t WHERE t.userId = :userId AND t.periodType = :periodType " +
           "AND t.periodKey = :periodKey AND t.labelName = :labelName AND t.total = 0")
   void deleteLabelZeroed(@Param("userId") UUID userId,
                          @Param("periodType") String periodType,
                          @Param("periodKey") String periodKey,
                          @Param("labelName") String labelName);
   ```

4. **Update existing `deleteZeroed`** — add condition `AND t.labelName IS NULL` to avoid accidentally deleting label rows:
   ```java
   @Query("DELETE FROM ExpenditurePeriodTotal t WHERE t.userId = :userId AND t.periodType = :periodType " +
           "AND t.periodKey = :periodKey AND t.labelName IS NULL AND t.total = 0")
   ```

5. **New query for day totals by label** — `sumDayTotalsByLabel(userId, types, yesterdayStart, todayStart, todayEnd)`:
   ```java
   @Query("SELECT l.name, " +
           "SUM(CASE WHEN t.transactionDate >= :yesterdayStart AND t.transactionDate < :todayStart THEN t.amount ELSE 0 END), " +
           "SUM(CASE WHEN t.transactionDate >= :todayStart AND t.transactionDate < :todayEnd THEN t.amount ELSE 0 END) " +
           "FROM Transaction t JOIN t.labels l " +
           "WHERE t.userId = :userId AND t.type IN (:types) " +
           "GROUP BY l.name")
   List<Object[]> sumDayTotalsByLabel(...);
   ```

6. **New query for unlabelled day totals** — `sumDayTotalsUnlabelled(userId, types, yesterdayStart, todayStart, todayEnd)`:
   ```java
   @Query("SELECT " +
           "SUM(CASE WHEN t.transactionDate >= :yesterdayStart AND t.transactionDate < :todayStart THEN t.amount ELSE 0 END), " +
           "SUM(CASE WHEN t.transactionDate >= :todayStart AND t.transactionDate < :todayEnd THEN t.amount ELSE 0 END) " +
           "FROM Transaction t " +
           "WHERE t.userId = :userId AND t.type IN (:types) AND t.labels IS EMPTY")
   List<Object[]> sumDayTotalsUnlabelled(...);
   ```

7. **New query for recompute** — `findExpenditureDateAmountsWithLabels(userId, types)` returning `[transactionDate, amount, labelName]` for per-label recomputation. Also a variant for unlabelled transactions.

8. **Update `findByUserIdAndPeriodTypeAndPeriodKey`** — add condition for `labelName IS NULL` to preserve existing behavior:
   ```java
   Optional<ExpenditurePeriodTotal> findByUserIdAndPeriodTypeAndPeriodKeyAndLabelNameIsNull(UUID userId, String periodType, String periodKey);
   ```

---

### Service Layer

#### [MODIFY] `src/main/java/com/budget/tracker/service/ExpenditureSummaryService.java`

**Write path changes:**

- `recordExpenditure` — **add new parameter** `Set<String> labelNames`:
  ```java
  public void recordExpenditure(UUID userId, OffsetDateTime transactionDate,
                                 TransactionType type, BigDecimal amount, Set<String> labelNames)
  ```
  - Existing logic: adjust overall week + month totals (unchanged)
  - New logic: for each `labelName` in the set, call `adjustLabelTotal(...)` for both week and month
  - If `labelNames` is empty, adjust `__UNLABELLED__` rows instead

- Same change for `removeExpenditure` — accept `Set<String> labelNames` and reverse per-label adjustments.

- `recomputeForUser` — rebuild per-label rows (and unlabelled rows) in addition to overall totals.

**Read path changes:**

- `buildSummary()` returns the updated `ExpenditureSummaryResponse` containing per-label breakdowns.
- For **today/yesterday**: run `sumDayTotalsByLabel` and `sumDayTotalsUnlabelled` queries.
- For **week/month**: load all `ExpenditurePeriodTotal` rows for the user (including those with `labelName != null`) and group them into per-period breakdown lists.
- Sort each breakdown list by amount descending.
- Map the sentinel `__UNLABELLED__` to the display name "Unlabelled" in the response.

#### [NEW] `src/main/java/com/budget/tracker/payload/response/LabelPeriodTotal.java`

A simple record to hold a label's expenditure for one period:
```java
public record LabelPeriodTotal(String labelName, BigDecimal amount) {}
```

#### [MODIFY] `src/main/java/com/budget/tracker/payload/response/ExpenditureSummaryResponse.java`

Add per-label breakdown lists for each period:

```diff
+    private List<LabelPeriodTotal> yesterdayByLabel = List.of();
+    private List<LabelPeriodTotal> todayByLabel = List.of();
+    private List<LabelPeriodTotal> lastWeekByLabel = List.of();
+    private List<LabelPeriodTotal> thisWeekByLabel = List.of();
+    private List<LabelPeriodTotal> lastMonthByLabel = List.of();
+    private List<LabelPeriodTotal> thisMonthByLabel = List.of();
```

Each list is sorted by amount descending (highest first). The `labelName` field contains the display name (e.g., "NEEDS", "WANTS", "Unlabelled").

---

### Callers of Write Path

#### [MODIFY] `src/main/java/com/budget/tracker/service/TransactionService.java`

Update all calls to `recordExpenditure` and `removeExpenditure` to pass the transaction's label names:

```java
Set<String> labelNames = saved.getLabels().stream()
    .map(Label::getName)
    .collect(Collectors.toSet());
expenditureSummaryService.recordExpenditure(userId, saved.getTransactionDate(),
    saved.getType(), saved.getAmount(), labelNames);
```

Specific call sites:
- `createTransaction(TransactionRequest)` (line 98) — extract labels from saved transaction
- `createTransaction(Transaction)` (line 117) — extract labels from saved transaction
- `updateTransaction(UUID, TransactionRequest)` (lines 189-191) — capture **old** label names before mutation for `removeExpenditure`, then use **new** label names for `recordExpenditure`
- `deleteTransaction(UUID)` (line 202) — extract labels before deletion

For `updateTransaction`, the old labels must be captured before they're modified:
```java
Set<String> oldLabelNames = existing.getLabels().stream()
    .map(Label::getName)
    .collect(Collectors.toSet());
// ... mutation happens ...
expenditureSummaryService.removeExpenditure(userId, oldDate, oldType, oldAmount, oldLabelNames);
Set<String> newLabelNames = saved.getLabels().stream()
    .map(Label::getName)
    .collect(Collectors.toSet());
expenditureSummaryService.recordExpenditure(userId, saved.getTransactionDate(),
    saved.getType(), saved.getAmount(), newLabelNames);
```

**Note:** Any other callers of `recordExpenditure` / `removeExpenditure` (e.g., backup restore in `BackupService`) must also be updated to pass label names.

---

### Frontend — Thymeleaf Template

#### [MODIFY] `src/main/resources/templates/fragments/period-cards.html`

Each period card will be restructured from an `<a>` wrapping everything to a `<div>` containing a link header and the always-visible breakdown:

```html
<div class="period-card">
    <a class="period-card-header" th:href="@{/transactions(startDate=..., endDate=...)}">
        <span class="period-card-label">Today</span>
        <span class="period-card-value" th:text="${fmt.format(expenditureSummary.today)}">₹0.00</span>
    </a>
    <div class="period-breakdown" th:unless="${#lists.isEmpty(expenditureSummary.todayByLabel)}">
        <div class="breakdown-row" th:each="lb : ${expenditureSummary.todayByLabel}">
            <span class="breakdown-label" th:text="${lb.labelName}">NEEDS</span>
            <span class="breakdown-value" th:text="${fmt.format(lb.amount)}">₹0.00</span>
        </div>
    </div>
</div>
```

This pattern is repeated for all 6 period cards (yesterday, today, lastWeek, thisWeek, lastMonth, thisMonth).

---

### Frontend — CSS

#### [MODIFY] `src/main/resources/static/css/style.css`

Add styles for the breakdown section within period cards:

```css
/* Period card header (replaces the old <a> card) */
.period-card-header {
    display: block;
    text-decoration: none;
    color: inherit;
}

/* Label breakdown */
.period-breakdown {
    margin-top: 0.5rem;
    padding-top: 0.5rem;
    border-top: 1px solid var(--border);
    display: flex;
    flex-direction: column;
    gap: 0.125rem;
}

.breakdown-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.breakdown-label {
    font-size: 0.6875rem;
    font-weight: 500;
    color: var(--muted-foreground);
    text-transform: uppercase;
    letter-spacing: 0.025em;
}

.breakdown-value {
    font-size: 0.8125rem;
    font-weight: 600;
    color: var(--foreground);
}
```

Update responsive grid — cards with breakdowns need more width, so adjust the large breakpoint:

```diff
 @media (min-width: 1024px) {
     .period-grid {
-        grid-template-columns: repeat(6, 1fr);
+        grid-template-columns: repeat(3, 1fr);
     }
 }
```

Also update the `.period-card` hover behavior since it's now a `<div>` not an `<a>`:

```diff
 .period-card {
     display: block;
     background: var(--muted);
     border: 1px solid var(--border);
     border-radius: var(--radius-lg);
     padding: 0.875rem 1rem;
-    text-decoration: none;
     transition: border-color 0.15s ease, transform 0.15s ease;
 }
```

---

### Test Changes

#### [MODIFY] `src/test/java/com/budget/tracker/service/ExpenditureSummaryServiceTest.java`

- Update all `recordExpenditure` / `removeExpenditure` calls to include `Set<String> labelNames` parameter
- Add new tests:
  - `recordExpenditure_withLabels_adjustsPerLabelRows` — verifies `adjustLabelTotal` is called for each label
  - `recordExpenditure_withNoLabels_adjustsUnlabelledRow` — verifies `__UNLABELLED__` row is adjusted
  - `removeExpenditure_withLabels_revertsPerLabelRows`
  - `getSummary_includesLabelBreakdownsSortedByAmount` — verifies per-label lists are populated and sorted descending
  - `getSummary_emptyLabelsReturnEmptyBreakdownLists`
  - `getSummary_unlabelledTransactionsAppearInBreakdown`
  - `recomputeForUser_rebuildsLabelAndUnlabelledRows`

#### [MODIFY] `src/test/java/com/budget/tracker/repository/ExpenditurePeriodTotalRepositoryTest.java`

- Add tests for `adjustLabelTotal` upsert (Postgres-only, using `assumePostgres()`)
- Add tests for `sumDayTotalsByLabel` query
- Add tests for `sumDayTotalsUnlabelled` query
- Add tests for `deleteLabelZeroed`
- Add test that overall-total rows and per-label rows coexist correctly

#### [MODIFY] `src/test/java/com/budget/tracker/web/DashboardViewControllerTest.java`

- Update mock `ExpenditureSummaryResponse` setup to include `*ByLabel` fields
- Add test that rendered HTML contains `period-breakdown` and `breakdown-row` elements when label data exists
- Add test that "Unlabelled" is rendered when unlabelled transactions exist

#### [MODIFY] `src/test/java/com/budget/tracker/controller/TransactionControllerTest.java`

- Verify the REST endpoint `GET /api/v1/transactions/expenditure-summary` returns the new `*ByLabel` fields in the JSON response

---

## Files Changed Summary

| Layer | File | Action |
|---|---|---|
| DB Migration | `src/main/resources/db/migration/V3__expenditure_period_totals_by_label.sql` | **NEW** |
| Model | `src/main/java/.../model/ExpenditurePeriodTotal.java` | MODIFY |
| Repository | `src/main/java/.../repository/ExpenditurePeriodTotalRepository.java` | MODIFY |
| DTO | `src/main/java/.../payload/response/LabelPeriodTotal.java` | **NEW** |
| DTO | `src/main/java/.../payload/response/ExpenditureSummaryResponse.java` | MODIFY |
| Service | `src/main/java/.../service/ExpenditureSummaryService.java` | MODIFY |
| Service | `src/main/java/.../service/TransactionService.java` | MODIFY |
| Template | `src/main/resources/templates/fragments/period-cards.html` | MODIFY |
| CSS | `src/main/resources/static/css/style.css` | MODIFY |
| Test | `src/test/.../service/ExpenditureSummaryServiceTest.java` | MODIFY |
| Test | `src/test/.../repository/ExpenditurePeriodTotalRepositoryTest.java` | MODIFY |
| Test | `src/test/.../web/DashboardViewControllerTest.java` | MODIFY |
| Test | `src/test/.../controller/TransactionControllerTest.java` | MODIFY |

---

## Verification Plan

### Automated Tests

```bash
# Run all tests
./gradlew test

# Run specific test classes
./gradlew test --tests "com.budget.tracker.service.ExpenditureSummaryServiceTest"
./gradlew test --tests "com.budget.tracker.repository.ExpenditurePeriodTotalRepositoryTest"
./gradlew test --tests "com.budget.tracker.web.DashboardViewControllerTest"
```

### Manual Verification

1. Start the application locally and navigate to the Dashboard
2. Verify each period card shows the total and per-label breakdown (always visible, no toggle)
3. Create transactions with different labels (NEEDS, WANTS, SAVINGS) and verify they appear in the breakdown sorted by amount
4. Create a transaction with **no labels** and verify it appears under "Unlabelled"
5. Create a transaction with **multiple labels** and verify its amount appears under each label
6. Verify the overall total still reflects the true expenditure (not inflated by multi-label counting)
7. Edit a transaction's labels and verify the breakdown updates correctly
8. Delete a transaction and verify the breakdown decrements
9. Test responsive layout on mobile (2-column grid) and desktop (3-column grid)
