# Test Coverage Report — Budget Tracker

> Functional coverage analysis across **44 Java test files** (unit, repository, integration, contract, web) and **20 Playwright E2E specs**. Coverage is assessed by *functionality*, not line counts.

---

## Test Inventory at a Glance

| Test Layer | Files | Approx. Test Methods |
|---|---|---|
| Controller (MockMvc) | 9 | ~35 |
| Service (Unit / Mockito) | 9 | ~55 |
| Repository (@DataJpaTest) | 8 | ~25 |
| Integration (full stack + DB) | 9 | ~30 |
| Security / Config / Model | 5 | ~20 |
| Contract / Web View | 2 | ~10 |
| **E2E (Playwright)** | **20** | **~60** |
| **Total** | **62** | **~235** |

---

## 1. Functionality Coverage Matrix

### ✅ Well-Covered Areas

#### Accounts
| Functionality | Unit | Repo | Integration | E2E |
|---|---|---|---|---|
| CRUD (create, read, update, delete) | ✅ | ✅ | ✅ | ✅ |
| Balance = initialBalance on create | ✅ | — | ✅ | ✅ |
| Balance adjusts on initialBalance update | ✅ | — | — | ✅ |
| Credit Card debt inversion (expense ↑, income ↓) | ✅ | — | ✅ | ✅ |
| Available credit calculation | ✅ | — | — | — |
| Account filter on Transactions page | — | — | — | ✅ |

#### Transactions
| Functionality | Unit | Repo | Integration | E2E |
|---|---|---|---|---|
| CRUD (create, read, update, delete) | ✅ | ✅ | ✅ | ✅ |
| INCOME increases balance / EXPENSE decreases | ✅ | — | ✅ | ✅ |
| LEND decreases / BORROW increases balance | ✅ | — | — | ✅ |
| Credit Card inversion on expense/income | ✅ | — | ✅ | — |
| Balance revert on delete | ✅ | — | ✅ | ✅ |
| Balance revert + re-apply on update | ✅ | — | ✅ | ✅ |
| Account change on update (dual balance) | ✅ | — | — | ✅ |
| Preserve category/labels when null on update | ✅ | — | — | ✅ |
| Zero / negative amount rejection | ✅ | — | — | ✅ |

#### Transfers
| Functionality | Unit | Repo | Integration | E2E |
|---|---|---|---|---|
| CRUD (create, read, update, delete) | ✅ | ✅ | ✅ | ✅ |
| Auto-compute 3rd field (from+adj→to, from+to→adj, to+adj→from) | ✅ | — | ✅ | ✅ |
| Reject all-three-fields provided | ✅ | — | — | — |
| Same-account rejection | — | — | — | — |
| Balance revert on delete/update | ✅ | — | ✅ | ✅ |
| Cross-user authorization checks | ✅ | — | — | — |
| Transfer filter by destination account | — | — | — | ✅ |
| Custom transfer descriptions | — | — | — | ✅ |

#### Categories
| Functionality | Unit | Repo | Integration | E2E |
|---|---|---|---|---|
| CRUD | ✅ | ✅ | ✅ | ✅ |
| Duplicate name rejection (case-insensitive) | ✅ | ✅ | ✅ | ✅ |
| Name trimming before save | ✅ | — | — | — |
| Default category seeding | ✅ | — | — | — |
| Emoji picker + keyword search | — | — | — | ✅ |
| Inline category creation from transaction form | — | — | — | ✅ |

#### Labels
| Functionality | Unit | Repo | Integration | E2E |
|---|---|---|---|---|
| CRUD | ✅ | ✅ | — | — |
| Pipe character `\|` rejection | ✅ | — | — | ✅ |
| Default label seeding | ✅ | — | — | — |
| Multi-label on transactions/transfers | — | — | — | ✅ |
| Label-based search | — | — | — | ✅ |

#### Expenditure Summary (Dashboard)
| Functionality | Unit | Repo | Integration | E2E |
|---|---|---|---|---|
| Record expense → upsert week+month rows | ✅ | — | — | — |
| Record LEND → positive delta on expenditure | ✅ | — | — | — |
| INCOME ignored by expenditure tracker | ✅ | — | — | — |
| Remove expenditure → negative delta + zero cleanup | ✅ | — | — | — |
| Recompute for user (full rebuild) | ✅ | — | — | — |
| getSummary (yesterday, today, this/last week/month) | ✅ | — | — | — |
| Missing rows yield zero | ✅ | — | — | — |
| sumDayTotals exclusive end boundary | — | ✅ | — | — |
| sumDayTotals user isolation | — | ✅ | — | — |
| adjustTotal upsert + accumulate | — | ✅* | — | — |
| deleteZeroed removes only zero rows | — | ✅* | — | — |

*\* These tests use `assumePostgres()` and are skipped on H2; only run in integration/Testcontainer profile.*

#### Authentication & Security
| Functionality | Unit | Controller | Integration | E2E |
|---|---|---|---|---|
| Register + Login flow | — | ✅ | ✅ | ✅ |
| Duplicate email rejection | — | ✅ | — | — |
| Wrong password → 401 | — | ✅ | ✅ | ✅ |
| JWT generation, validation, expiry | ✅ | — | — | — |
| JWT cookie flags (HttpOnly, Secure, Lax) | — | — | — | ✅ |
| CSRF header filter (HX-Request / Bearer bypass) | ✅ | — | — | — |
| CORS enabled / disabled profiles | ✅ | — | — | ✅ |

#### Backups
| Functionality | Unit | Controller | Integration | E2E |
|---|---|---|---|---|
| Export to SQL / CSV | ✅ | ✅ | ✅ | ✅ |
| Import from SQL / CSV | ✅ | ✅ | ✅ | ✅ |
| Download backup file | ✅ | ✅ | — | ✅ |
| Unauthorized access rejection | ✅ | — | — | — |
| Clear all user data | — | — | — | ✅ |
| Auto-backup scheduler (daily/weekly/monthly) | ✅ | — | — | — |
| Auto-backup frequency throttling | ✅ | — | — | — |

#### User Preferences
| Functionality | Unit | Controller | Integration | E2E |
|---|---|---|---|---|
| Fetch / Update preferences | ✅ | ✅ | ✅ | ✅ |
| Default account/type/label prepopulation | — | — | — | ✅ |

#### UI / Frontend
| Functionality | E2E |
|---|---|
| Theme selection (light/dark/OLED) + persistence | ✅ |
| Mobile logout button | ✅ |
| Transaction amount input behavior | ✅ |
| Transaction/transfer placeholder text | ✅ |
| Spending Insights removal verification | ✅ |

---

## 2. Coverage Gaps (Untested Functionality)

> [!IMPORTANT]
> These are functional areas in production code that lack any direct test coverage.

### 🔴 Critical Gaps

| # | Area | Detail |
|---|---|---|
| 1 | **ActivityService** (unit tests) | No dedicated `ActivityServiceTest.java`. The `ActivityService` has complex logic: label population via raw SQL joins (`populateLabels`), search-term filtering with category+label matching, UUID byte-array conversion. Only indirectly tested through controller + integration tests. |
| 2 | **Web View Controllers** (unit/integration) | `TransactionsViewController`, `SettingsViewController`, `BackupsViewController`, `AuthPagesController`, `LandingController` have **zero** backend test coverage. These controllers contain non-trivial logic: form parameter parsing, error toast generation, JSON escaping, date parsing, default prepopulation. Only tested indirectly by E2E. |
| 3 | **Transfer same-account validation** | `TransferService.createTransfer()` rejects `fromAccountId == toAccountId` (line 78), but no unit or integration test exercises this path. |
| 4 | **Transfer credit card balance inversion** | `TransferService.adjustAccountBalance()` has credit-card-specific inversion logic (line 242-244) for transfers, but no unit test covers transferring into/out of a credit card account. The transaction-level CC inversion is well tested, but transfer-level is not. |
| 5 | **BackupService.importFromCsv — account/category/label auto-creation** | CSV import creates new Accounts, Categories, and Labels on-the-fly when names don't match existing entities. No test verifies this fallback creation logic. |
| 6 | **`GlobalExceptionHandler`** | No dedicated tests for the exception handler. `MethodArgumentNotValidException` field-concatenation behavior is completely untested. |

### 🟡 Moderate Gaps

| # | Area | Detail |
|---|---|---|
| 7 | **ExpenditureSummaryService — cross-period boundary updates** | When a transaction date is modified to a different week/month, the old period should be decremented and the new one incremented. No test exercises a date-change scenario that crosses period boundaries. |
| 8 | **BackupService.exportToSql — SQL escaping** | The `escapeSql()` method only handles single-quote escaping. No test verifies it handles edge cases (e.g., descriptions containing `'`, `NULL`, special characters). |
| 9 | **PageContextInterceptor / CurrencyFormatter / WebConfig** | No tests for the interceptor that injects theme, currency formatter, and user info into every Thymeleaf render. |
| 10 | **AuthTokenFilter — cookie-based JWT extraction** | The filter supports JWT from both `Authorization: Bearer` header AND a `jwt` cookie. No unit test exercises the cookie fallback path specifically. |
| 11 | **UserDetailsServiceImpl** | No dedicated unit test. Only indirectly tested through integration tests. |
| 12 | **ActivityRepository.searchActivity** — date-range filtering | Integration tests check search/type filters, but date-range bounds (`startDate`/`endDate`) are not verified. |
| 13 | **Label integration test** | Unlike Account, Category, and Transaction, there is no `LabelIntegrationTest.java` verifying full API round-trip with a real database. |

---

## 3. Tests That Need Correction

> [!WARNING]
> These tests contain logic issues that could mask bugs.

### 🐛 Bug 1: Coincidental Pass in `sumDayTotals_usesExclusiveEndBoundaries`

**File**: [ExpenditurePeriodTotalRepositoryTest.java](file:///Users/shubham/IdeaProjects/budget_tracker/src/test/java/com/budget/tracker/repository/ExpenditurePeriodTotalRepositoryTest.java#L74-L92)

**Problem**: The test creates these transactions:
| When | Amount | Type |
|---|---|---|
| Yesterday | 20.00 | EXPENSE |
| Today (start) | 10.00 | EXPENSE |
| Today (just before end) | 30.00 | LEND |
| Tomorrow (excluded) | **40.00** | EXPENSE |
| Today (not expenditure) | 50.00 | INCOME |

It then asserts `totals[1]` (today) = **40.00** (10 + 30 = 40). However, the excluded tomorrow amount is *also* 40.00.

**Risk**: If the query bug was "select tomorrow's single record instead of summing today's records", the assertion would still pass. This is a **false-positive-capable** test.

**Fix**: Change tomorrow's amount to a distinct value (e.g., `77.00`) so the test can only pass by correctly summing today's records:
```java
transactionRepository.save(expenseAt(todayEnd, "77.00", TransactionType.EXPENSE)); // tomorrow - excluded
```

---

### ⚠️ Weakness 2: `validation.spec.ts` — Browser `alert()` Coupling

**File**: [validation.spec.ts](file:///Users/shubham/IdeaProjects/budget_tracker/e2e/tests/validation.spec.ts)

**Problem**: This E2E test intercepts a raw `window.alert()` dialog for zero-amount validation. The rest of the app uses HTMX toast notifications for errors. This creates two concerns:
1. **Inconsistent UX** — if validation is migrated to toasts, this test will break silently (no alert = test times out or fails for wrong reason)
2. **Fragile pattern** — `page.on('dialog')` is inherently timing-sensitive

**Recommendation**: This isn't strictly *wrong* (it tests what's currently implemented), but it should be noted as a tech-debt item to align with the toast notification pattern used everywhere else.

---

## 4. Test Quality Observations

### 👍 Strengths
- **Balance math is extensively verified** across unit, integration, and E2E layers for all transaction types
- **Credit card inversion** is well-tested at the transaction level
- **Transfer auto-computation** (3-field algebra) has thorough unit and integration tests
- **Cross-user isolation** is tested at repository and service layers
- **Contract test** (`DashboardInsightsRemovalContractTest`) is a novel approach that ensures removed features stay removed across templates, CSS, and controllers
- **E2E test isolation** is excellent — each test registers a fresh user via API

### 👎 Weaknesses
- **No `ActivityServiceTest`** despite it being one of the most complex services (raw SQL, UUID conversion, search filtering)
- **Web view controllers** contain meaningful logic (date parsing, error handling, default prepopulation) with zero backend tests — entirely reliant on E2E
- **Expenditure period boundary edge cases** are undertested — modifying a transaction's date across week/month boundaries is a realistic scenario with no coverage
- **`assumePostgres()` tests** silently skip on H2 — the standard `./gradlew test` run misses `adjustTotal` and `deleteZeroed` verification entirely
