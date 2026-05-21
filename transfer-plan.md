# Plan: Adjustment Tracking for Transfers + Separate Transfer Table + Unified Activity View

## Context

Currently, transfers use a single `amount` field — the same amount debited from the source account is credited to the destination. Real-world transfers often differ: e.g., pay ₹95 from bank for a ₹100 credit card bill (₹5 adjustment/savings), or earn cashback. This change adds adjustment tracking to transfers, creates a dedicated `transfers` table separate from `transactions`, and introduces a unified `activity_view` for combined listing.

## Scope

1. **New `transfers` table** — separate from `transactions` with `from_amount`, `adjustment` columns; `to_amount` computed as `from_amount + adjustment`.
2. **Backend** — new `Transfer` entity, `TransferRepository`, `TransferService`, `TransferController`, `TransferRequest` with three fields (any two required).
3. **Balance logic** — source account debited `from_amount`, destination account credited `to_amount`.
4. **Unified activity view** — PostgreSQL VIEW (`activity_view`) that UNIONs transactions and transfers for combined, paginated listing. Backed by read-only `ActivityItem` JPA entity, `ActivityRepository`, `ActivityService`, and `GET /api/v1/activity` endpoint.
5. **Frontend** — Transfer form shows three fields: From Amount, To Amount, Adjustment. Any two populated → third calculated automatically. Transactions page and Dashboard use unified `/activity` endpoint.
6. **Tests** — unit, integration, frontend component, and E2E for the full path.

---

## Phase 1: Database — Modify Initial Schema

**File:** `src/main/resources/db/migration/V1__initial_schema.sql` (edit in place — not deployed, so no V2 migration needed)

### 1a. Add `transfers` table

```sql
CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    from_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    to_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    label_id UUID REFERENCES labels(id) ON DELETE SET NULL,
    from_amount DECIMAL(19, 4) NOT NULL,
    adjustment DECIMAL(19, 4) NOT NULL DEFAULT 0,
    to_amount DECIMAL(19, 4) NOT NULL,  -- stored; computed by service as from_amount + adjustment before save
    description TEXT,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_transfers_user_id ON transfers(user_id);
CREATE INDEX idx_transfers_from_account_id ON transfers(from_account_id);
CREATE INDEX idx_transfers_to_account_id ON transfers(to_account_id);
CREATE INDEX idx_transfers_date ON transfers(transaction_date);
```

### 1b. Clean up `transactions` table — remove transfer-specific columns

Drop `to_account_id` column and associated index:
```sql
ALTER TABLE transactions DROP COLUMN IF EXISTS to_account_id;
DROP INDEX IF EXISTS idx_transactions_to_account_id;
```

Remove `TRANSFER` from the `transaction_type` enum:
```sql
ALTER TYPE transaction_type RENAME TO transaction_type_old;
CREATE TYPE transaction_type AS ENUM ('INCOME', 'EXPENSE', 'LEND', 'BORROW');
ALTER TABLE transactions ALTER COLUMN type TYPE transaction_type
  USING type::text::transaction_type;
DROP TYPE transaction_type_old;
```

### 1c. Clean up indexes section
Remove `CREATE INDEX idx_transactions_to_account_id ON transactions(to_account_id);` from the indexes block at the bottom of V1.

### 1d. Create `activity_view` — unified read view

```sql
CREATE VIEW activity_view AS
SELECT
    id,
    'TRANSACTION' AS kind,
    user_id,
    account_id,
    NULL::uuid AS to_account_id,
    category_id,
    label_id,
    amount,
    type::text AS type,
    NULL::decimal(19,4) AS from_amount,
    NULL::decimal(19,4) AS to_amount,
    NULL::decimal(19,4) AS adjustment,
    description,
    transaction_date,
    created_at
FROM transactions
UNION ALL
SELECT
    id,
    'TRANSFER' AS kind,
    user_id,
    from_account_id AS account_id,
    to_account_id,
    category_id,
    label_id,
    NULL::decimal(19,4) AS amount,
    'TRANSFER' AS type,
    from_amount,
    to_amount,
    adjustment,
    description,
    transaction_date,
    created_at
FROM transfers;
```

This view normalizes both tables into a single shape. The `kind` column discriminates between `'TRANSACTION'` and `'TRANSFER'`. The `type` column maps to the transaction type for transactions and `'TRANSFER'` for transfers. The `account_id` column always represents the primary account (source for transfers), and `to_account_id` is the destination (null for non-transfer transactions).

---

## Phase 2: Backend — Transfer Entity & Activity Layer

### 2a. Transfer Entity

**File:** `src/main/java/com/budget/tracker/model/Transfer.java`

```
- extends BaseEntity
- @ManyToOne LAZY Account fromAccount (account_id → from_account_id)
- @ManyToOne LAZY Account toAccount
- @ManyToOne LAZY Category category (nullable)
- @ManyToOne LAZY Label label (nullable)
- BigDecimal fromAmount (not null)
- BigDecimal adjustment (not null, default 0)
- BigDecimal toAmount — regular `@Column`. Service always computes and sets `toAmount = fromAmount + adjustment` before every save (create, update). Simple, no @Formula, no generated columns, no H2 issues.
- String description
- OffsetDateTime transactionDate
- UUID userId
- @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) — prevents "to undefined" serialization bug (same fix used in Transaction.java)
```

**Lazy loading safety**: All repository queries MUST use `LEFT JOIN FETCH` for `fromAccount`, `toAccount`, `category`, and `label` — identical to how `TransactionRepository` queries work. Additionally, set `spring.jpa.open-in-view=false` (already configured in test profile via `TransactionSerializationTest`) to catch lazy-loading bugs early. A `TransferSerializationTest` will verify all associations serialize correctly after session close.

### 2b. TransferRepository

**File:** `src/main/java/com/budget/tracker/repository/TransferRepository.java`

- `findAllByUserId(userId)` — LEFT JOIN FETCH fromAccount, toAccount, category, label
- `findAccountTransactions(accountId, userId)` — LEFT JOIN FETCH all, where fromAccount OR toAccount matches
- `searchTransfers(userId, searchTerm, startDate, endDate, pageable)` — LEFT JOIN FETCH all, like TransactionRepository search
- Standard JpaRepository[Transfer, UUID]

### 2c. Request DTO

**File:** `src/main/java/com/budget/tracker/payload/request/TransferRequest.java` (update)

Current fields: `fromAccountId`, `toAccountId`, `amount`, `transactionDate`, `description`, `categoryId`

New fields: `fromAmount`, `toAmount`, `adjustment` (all BigDecimal, nullable), `labelId` (UUID, optional)
Remove: `amount`

Validation: Exactly **two** of `{fromAmount, toAmount, adjustment}` must be non-null. Third is computed:
- fromAmount + toAmount present → adjustment = toAmount - fromAmount
- fromAmount + adjustment present → toAmount = fromAmount + adjustment
- toAmount + adjustment present → fromAmount = toAmount - adjustment

### 2d. Response DTO

**File:** `src/main/java/com/budget/tracker/payload/response/TransferResponse.java`

Fields: `id`, `fromAmount`, `toAmount`, `adjustment`, `description`, `transactionDate`, `fromAccount`, `toAccount`, `category`, `label`, `createdAt`

### 2e. TransferService

**File:** `src/main/java/com/budget/tracker/service/TransferService.java`

- `createTransfer(TransferRequest)` — validate, compute missing field, debit fromAccount `fromAmount`, credit toAccount `toAmount`, save
- `getTransferById(id)` — user-scoped
- `getAllTransfers(searchTerm, startDate, endDate, pageable)` — user-scoped
- `getTransfersForAccount(accountId)` — where from or to account matches
- `updateTransfer(id, TransferRequest)` — revert old balances, apply new
- `deleteTransfer(id)` — revert balances, delete
- Balance adjustment reuses the same logic as `TransactionService.updateBalance()` — extract shared helper or delegate to a common `BalanceAdjustmentService`

### 2f. TransferController

**File:** `src/main/java/com/budget/tracker/controller/TransferController.java`

- `GET /api/v1/transfers` — paginated, searchable
- `GET /api/v1/transfers/{id}` — single transfer
- `POST /api/v1/transfers` — create
- `PUT /api/v1/transfers/{id}` — update
- `DELETE /api/v1/transfers/{id}` — delete

### 2g. ActivityItem Entity (read-only)

**File:** `src/main/java/com/budget/tracker/model/ActivityItem.java`

```
- @Entity mapped to `activity_view`
- @Immutable — read-only, no inserts/updates/deletes
- @Id UUID id
- String kind ("TRANSACTION" or "TRANSFER")
- String type ("INCOME", "EXPENSE", "TRANSFER", "LEND", "BORROW")
- UUID userId
- @ManyToOne LAZY Account account (account_id — source for both types)
- @ManyToOne LAZY Account toAccount (to_account_id — null for transactions)
- @ManyToOne LAZY Category category (nullable)
- @ManyToOne LAZY Label label (nullable)
- BigDecimal amount (non-null for transactions, null for transfers)
- BigDecimal fromAmount (non-null for transfers, null for transactions)
- BigDecimal toAmount (non-null for transfers, null for transactions)
- BigDecimal adjustment (non-null for transfers, null for transactions)
- String description
- OffsetDateTime transactionDate
- OffsetDateTime createdAt
```

All `@ManyToOne` relationships are LAZY and use the same FK columns as the view's output. Since both `transactions.account_id` and `transfers.from_account_id` reference `accounts(id)`, JPA resolves the join correctly.

### 2h. ActivityRepository

**File:** `src/main/java/com/budget/tracker/repository/ActivityRepository.java`

- `findAllByUserId(userId, pageable)` — LEFT JOIN FETCH account, toAccount, category, label. Returns `Page<ActivityItem>`.
- `searchActivity(userId, searchTerm, type, startDate, endDate, pageable)` — paginated search with optional filters. The `type` filter accepts any of: INCOME, EXPENSE, TRANSFER, LEND, BORROW.
- `findByAccountId(userId, accountId)` — returns all activity where `account.id = accountId OR toAccount.id = accountId`. Used for account detail views.

### 2i. ActivityService

**File:** `src/main/java/com/budget/tracker/service/ActivityService.java`

- `getActivity(searchTerm, type, startDate, endDate, pageable)` — user-scoped, delegates to ActivityRepository
- `getActivityForAccount(accountId)` — user-scoped
- Maps `ActivityItem` entities to `ActivityResponse` DTOs

### 2j. ActivityController

**File:** `src/main/java/com/budget/tracker/controller/ActivityController.java`

- `GET /api/v1/activity` — paginated, searchable, filterable by type and date range. Replaces `GET /transactions` as the primary listing endpoint for the frontend.

### 2k. ActivityResponse DTO

**File:** `src/main/java/com/budget/tracker/payload/response/ActivityResponse.java`

Fields: `id`, `kind`, `type`, `amount`, `fromAmount`, `toAmount`, `adjustment`, `description`, `transactionDate`, `account`, `toAccount`, `category`, `label`, `createdAt`

### 2l. DataSeeder — Use TransferService for Demo Transfer

**File:** `src/main/java/com/budget/tracker/util/DataSeeder.java`

Current transfer seeding (lines 120-128) uses:
```java
Transaction transfer = new Transaction();
transfer.setAccount(mainBank);
transfer.setAmount(new BigDecimal("50.00"));
transfer.setType(TransactionType.TRANSFER);          // REMOVED
transfer.setDescription("ATM Withdrawal");
transfer.setTransactionDate(OffsetDateTime.now());
transactionService.createTransfer(transfer, cash);   // REMOVED
```

Changes:
- Inject `TransferService` as a constructor dependency
- Replace with new `Transfer` entity + `TransferRequest`:
  ```java
  TransferRequest transferReq = new TransferRequest();
  transferReq.setFromAccountId(mainBank.getId());
  transferReq.setToAccountId(cash.getId());
  transferReq.setFromAmount(new BigDecimal("50.00"));
  transferReq.setAdjustment(new BigDecimal("5.00"));     // showcases adjustment feature
  transferReq.setDescription("ATM Withdrawal");
  transferReq.setTransactionDate(OffsetDateTime.now());
  transferService.createTransfer(transferReq);
  ```
- This seeds a transfer with fromAmount=50, adjustment=5 → toAmount=55. Demonstrates the adjustment feature in demo mode.
- Remove `TransactionType.TRANSFER` import (enum value removed)

### 2m. TransactionController — Remove Transfer Endpoint

**File:** `src/main/java/com/budget/tracker/controller/TransactionController.java`

- Remove `POST /api/v1/transactions/transfer` endpoint
- Keep `GET /api/v1/transactions` (still useful for API consumers who want only transactions, not transfers)

---

## Phase 3: Frontend — Transfer Form & Unified Activity List

### 3a. Types

**File:** `frontend/src/types/transfer.ts` (new)

```ts
export interface Transfer {
  id: string;
  fromAmount: number;
  toAmount: number;
  adjustment: number;
  description?: string;
  transactionDate: string;
  fromAccountId: string;
  fromAccount?: Account;
  toAccountId: string;
  toAccount?: Account;
  categoryId?: string;
  category?: Category;
  labelId?: string;
  label?: Label;
  createdAt: string;
}

export interface CreateTransferRequest {
  fromAmount?: number;
  toAmount?: number;
  adjustment?: number;
  transactionDate: string;
  fromAccountId: string;
  toAccountId: string;
  categoryId?: string;
  labelId?: string;
  description?: string;
}
```

**File:** `frontend/src/types/activity.ts` (new)

```ts
export type ActivityKind = 'TRANSACTION' | 'TRANSFER';
export type ActivityType = 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'LEND' | 'BORROW';

export interface ActivityItem {
  id: string;
  kind: ActivityKind;
  type: ActivityType;
  amount?: number;        // present for transactions
  fromAmount?: number;    // present for transfers
  toAmount?: number;      // present for transfers
  adjustment?: number;    // present for transfers
  description?: string;
  transactionDate: string;
  account?: Account;
  toAccount?: Account;
  category?: Category;
  label?: Label;
  createdAt: string;
}
```

### 3b. TransactionForm — Three Amount Fields for Transfer

**File:** `frontend/src/components/TransactionForm.tsx`

When `type === 'TRANSFER'`:
- Replace single `Amount` input with three fields in a row: `From Amount`, `To Amount`, `Adjustment`
- On blur/change of any field: if two fields are populated, compute the third
  - `fromAmount` + `toAmount` filled → `adjustment = toAmount - fromAmount`
  - `fromAmount` + `adjustment` filled → `toAmount = fromAmount + adjustment`
  - `toAmount` + `adjustment` filled → `fromAmount = toAmount - adjustment`
- Validation: at least two fields must be non-empty and positive
- On submit for TRANSFER: pass `fromAmount`, `toAmount`, `adjustment` (whichever two the user filled) up to the parent
- Keep existing single `Amount` field for INCOME/EXPENSE/LEND/BORROW types

### 3c. Layout.tsx — Update Transfer API Routing

**File:** `frontend/src/components/Layout.tsx`

The `handleCreateTransaction` function (lines 44-76) currently branches for TRANSFER:
```ts
if (data.type === 'TRANSFER') {
  const transferPayload = {
    fromAccountId: data.accountId,
    toAccountId: data.toAccountId,
    amount: data.amount,
    description: data.description || '',
    transactionDate: data.transactionDate,
    categoryId: data.categoryId
  };
  await apiClient.post('/transactions/transfer', transferPayload);
}
```

Update to:
```ts
if (data.type === 'TRANSFER') {
  const transferPayload = {
    fromAccountId: data.accountId,
    toAccountId: data.toAccountId,
    fromAmount: data.fromAmount,
    toAmount: data.toAmount,
    adjustment: data.adjustment,
    description: data.description || '',
    transactionDate: data.transactionDate,
    categoryId: data.categoryId,
    labelId: data.labelId
  };
  await apiClient.post('/transfers', transferPayload);
}
```

Key changes:
- Endpoint: `/transactions/transfer` → `/transfers`
- Payload: single `amount` → `fromAmount`, `toAmount`, `adjustment` (two of three)
- Add `labelId` support (currently missing for transfers)

### 3d. TransactionList — Show Transfer Details

**File:** `frontend/src/components/TransactionList.tsx`

- Update to accept `ActivityItem` type instead of `Transaction`
- For TRANSFER kind: show `fromAmount` with `-`, show `toAccount` arrow, optionally show adjustment badge if adjustment > 0
- Existing display logic for `description` and `From → To` arrow already works (from recent commits)
- For TRANSACTION kind: display as before using `amount` field

### 3e. Transactions Page — Use Unified Activity Endpoint

**File:** `frontend/src/pages/Transactions.tsx`

- Change fetch URL from `GET /transactions` to `GET /activity`
- The response now contains `ActivityItem` objects (both transactions and transfers)
- Filter dropdown retains all types: ALL, INCOME, EXPENSE, TRANSFER, LEND, BORROW — the `type` query param is passed to the activity endpoint which filters at the database level
- Pagination continues to work unchanged (server-side via the activity view)

### 3f. Dashboard — Use Unified Activity Endpoint

**File:** `frontend/src/pages/Dashboard.tsx`

- Update recent transactions fetch from `GET /transactions` to `GET /activity`
- Use `ActivityItem` type for the response data
- The `TransactionList` component (updated in 3d) handles both transactions and transfers in the unified list
- Analytics fetch can continue using `/transactions` for transaction-specific analytics, or switch to `/activity` if transfer amounts should be included

---

## Phase 4: Tests

### 4a. Backend Unit Tests — Transfer

**File:** `src/test/java/com/budget/tracker/service/TransferServiceTest.java` (new)

- `createTransfer_fromAmount_adjustment_shouldComputeToAmountAndAdjustBalances`
- `createTransfer_fromAmount_toAmount_shouldComputeAdjustmentAndAdjustBalances`
- `createTransfer_toAmount_adjustment_shouldComputeFromAmountAndAdjustBalances`
- `createTransfer_allThreeFields_shouldReject` (only allow exactly two)
- `createTransfer_zeroFromAmount_shouldThrow`
- `createTransfer_sameAccount_shouldThrow`
- `createTransfer_creditCardDestination_debtDecreases` — fromAmount debited from bank, toAmount reduces CC debt
- `createTransfer_adjustmentScenario_bank95_cc100_adjustment5` — the main use case
- `updateTransfer_shouldRevertAndApplyBalances`
- `deleteTransfer_shouldReverseBalances`
- `getTransfersForAccount_shouldReturnFromAndTo`

**File:** `src/test/java/com/budget/tracker/controller/TransferControllerTest.java` (new)

- `shouldCreateTransfer` — POST /transfers with fromAmount + adjustment
- `shouldGetAllTransfers` — GET /transfers
- `shouldDeleteTransfer` — DELETE /transfers/{id}
- `shouldRejectInvalidTransfer` — all three fields / no fields

### 4b. Backend Unit Tests — Activity

**File:** `src/test/java/com/budget/tracker/controller/ActivityControllerTest.java` (new)

- `shouldGetAllActivity` — GET /activity returns both transactions and transfers
- `shouldFilterActivityByType` — GET /activity?type=TRANSFER returns only transfers
- `shouldSearchActivity` — GET /activity?search=ATM returns matching items
- `shouldPaginateActivity` — GET /activity?page=0&size=5 returns correct page

### 4c. Repository Tests

**File:** `src/test/java/com/budget/tracker/repository/TransferRepositoryTest.java` (new)

- `findAllByUserId_shouldReturnUserTransfers` — DataJpaTest with H2
- `findAccountTransactions_shouldMatchFromOrTo`
- `searchTransfers_shouldFilterByDescription`

### 4d. Integration Tests

**File:** `src/test/java/com/budget/tracker/integration/TransferIntegrationTest.java` (new)

- `testTransferWithAdjustment` — Register → Login → Create Bank (₹1000) + CC (₹500 debt) → POST /transfers fromAmount=95, adjustment=5 → Verify bank=905, cc-debt=400, toAmount=100
- `testTransferWithFromAndToAmounts` — fromAmount=90, toAmount=100 → adjustment=10 computed
- `testTransferToAmountSerialization` — Like TransactionSerializationTest but for transfers
- `testUpdateTransfer` — Create → Update amounts → Verify balance adjustments
- `testDeleteTransfer` — Create → Delete → Verify balances revert
- `testSearchTransfers` — Verify description search works for transfers

**File:** `src/test/java/com/budget/tracker/integration/ActivityIntegrationTest.java` (new)

- `testUnifiedActivityList` — Create transactions + transfers → GET /activity → Verify both appear, sorted by date
- `testActivityFilterByType` — GET /activity?type=TRANSFER → only transfers returned
- `testActivityPagination` — Create many items → verify page size and ordering
- `testActivityForAccount` — Verify account-specific activity includes both transactions and transfers for that account

### 4e. Frontend Component Tests

**File:** `frontend/src/components/TransactionForm.test.tsx` (update)

- `computes toAmount when fromAmount and adjustment are entered for TRANSFER`
- `computes adjustment when fromAmount and toAmount are entered for TRANSFER`
- `computes fromAmount when toAmount and adjustment are entered for TRANSFER`
- `rejects TRANSFER submission when less than two amount fields are filled`
- `submits transfer with correct payload to transfers endpoint`

**File:** `frontend/src/components/TransferList.test.tsx` (new or integrated)

- `displays transfer with from → to account arrow`
- `displays adjustment badge when adjustment > 0`

### 4f. E2E Tests

**File:** `e2e/tests/transactions.spec.ts` (update — update existing 4 transfer tests)

Existing tests that need updating to use three-field form (From Amount, To Amount, Adjustment) instead of single Amount field:
1. `should allow transferring money between accounts` (line 60)
2. `should allow transferring from Cash to Bank` (line 98)
3. `should display custom description and arrow for transfers` (line 138)
4. `should correctly handle bank to credit card transfer (paying bill)` (line 168)

New tests to add:
- `should allow transferring with adjustment (savings tracking)` — Create Bank + CC → Transfer with fromAmount=95, adjustment=5 → Verify bank=905, cc-debt=400
- `should compute toAmount from fromAmount + adjustment in UI`
- `should compute adjustment from toAmount - fromAmount in UI`
- `should show adjustment badge in transfer list`

**File:** `e2e/tests/transfers.spec.ts` (new)

- `should create transfer and verify balances`
- `should search transfers by description`
- `should handle transfer to credit card with adjustment`

---

## Phase 5: Existing Code & Test Updates

- **`TransactionServiceTest.java`** — Remove transfer-related tests (createTransfer, updateTransaction_transferType, deleteTransaction_transfer). Update `createTransaction_transferType_shouldThrow` message.
- **`TransactionControllerTest.java`** — Remove `shouldCreateTransfer` test. Keep other transaction tests.
- **`TransactionIntegrationTest.java`** — Remove transfer integration tests (moved to TransferIntegrationTest).
- **`e2e/tests/transactions.spec.ts`** — Transfer tests updated to use new form fields and endpoint.
- **`TransactionRepository.java`** — Remove `to_account_id` and `toAccount` from queries since transfers are in their own table.
- **`Transaction.java`** — Remove `toAccount` field entirely (schema already cleaned up in V1 initial schema).
- **`DataSeeder.java`** — Inject `TransferService`, replace old `TransactionType.TRANSFER` + `transactionService.createTransfer()` call with `TransferService.createTransfer(TransferRequest)`. Demo transfer showcases adjustment feature (fromAmount=50, adjustment=5).
- **`TransactionType.java`** — Remove `TRANSFER` enum value.
- **`TransactionService.java`** — Remove `createTransfer()` method and transfer-specific logic from `updateTransaction()` and `deleteTransaction()`.
- **`frontend/src/types/transaction.ts`** — Remove `TRANSFER` from `TransactionType` union.
- **`frontend/src/components/Layout.tsx`** — Update transfer API routing (see Phase 3c).

---

## File Change Summary

| Action | File |
|--------|------|
| **Update** | `src/main/resources/db/migration/V1__initial_schema.sql` — Add transfers table, activity_view, drop to_account_id, remove TRANSFER from enum |
| **New** | `src/main/java/com/budget/tracker/model/Transfer.java` |
| **New** | `src/main/java/com/budget/tracker/model/ActivityItem.java` — read-only entity mapped to `activity_view` |
| **New** | `src/main/java/com/budget/tracker/repository/TransferRepository.java` |
| **New** | `src/main/java/com/budget/tracker/repository/ActivityRepository.java` |
| **New** | `src/main/java/com/budget/tracker/service/TransferService.java` |
| **New** | `src/main/java/com/budget/tracker/service/ActivityService.java` |
| **New** | `src/main/java/com/budget/tracker/controller/TransferController.java` |
| **New** | `src/main/java/com/budget/tracker/controller/ActivityController.java` |
| **New** | `src/main/java/com/budget/tracker/payload/response/TransferResponse.java` |
| **New** | `src/main/java/com/budget/tracker/payload/response/ActivityResponse.java` |
| **Update** | `src/main/java/com/budget/tracker/payload/request/TransferRequest.java` — replace `amount` with `fromAmount`/`toAmount`/`adjustment`, add `labelId` |
| **Update** | `src/main/java/com/budget/tracker/service/TransactionService.java` — remove transfer logic |
| **Update** | `src/main/java/com/budget/tracker/controller/TransactionController.java` — remove /transfer endpoint |
| **Update** | `src/main/java/com/budget/tracker/model/Transaction.java` — remove toAccount |
| **Update** | `src/main/java/com/budget/tracker/repository/TransactionRepository.java` — remove toAccount fetch |
| **Update** | `src/main/java/com/budget/tracker/model/TransactionType.java` — remove TRANSFER enum value |
| **Update** | `src/main/java/com/budget/tracker/util/DataSeeder.java` — inject TransferService, use new transfer API |
| **New** | `frontend/src/types/transfer.ts` |
| **New** | `frontend/src/types/activity.ts` |
| **Update** | `frontend/src/types/transaction.ts` — remove TRANSFER from TransactionType |
| **Update** | `frontend/src/components/TransactionForm.tsx` — three amount fields for transfers |
| **Update** | `frontend/src/components/Layout.tsx` — route transfers to POST /transfers with new payload + labelId |
| **Update** | `frontend/src/components/TransactionList.tsx` — handle ActivityItem type with transfer adjustment display |
| **Update** | `frontend/src/pages/Transactions.tsx` — fetch from /activity endpoint |
| **Update** | `frontend/src/pages/Dashboard.tsx` — fetch from /activity endpoint |
| **New** | `src/test/java/com/budget/tracker/service/TransferServiceTest.java` |
| **New** | `src/test/java/com/budget/tracker/controller/TransferControllerTest.java` |
| **New** | `src/test/java/com/budget/tracker/controller/ActivityControllerTest.java` |
| **New** | `src/test/java/com/budget/tracker/repository/TransferRepositoryTest.java` |
| **New** | `src/test/java/com/budget/tracker/integration/TransferIntegrationTest.java` |
| **New** | `src/test/java/com/budget/tracker/integration/ActivityIntegrationTest.java` |
| **Update** | `frontend/src/components/TransactionForm.test.tsx` |
| **Update** | `src/test/java/com/budget/tracker/service/TransactionServiceTest.java` — remove transfer tests |
| **Update** | `src/test/java/com/budget/tracker/controller/TransactionControllerTest.java` — remove transfer test |
| **Update** | `src/test/java/com/budget/tracker/integration/TransactionIntegrationTest.java` — remove transfer tests |
| **Update** | `e2e/tests/transactions.spec.ts` — update 4 existing transfer tests for new form fields |
| **New** | `e2e/tests/transfers.spec.ts` |

---

## Verification

1. `./gradlew test` — All Java unit + repository tests pass
2. `make test-int` — All integration tests pass (including TransferIntegrationTest + ActivityIntegrationTest)
3. `cd frontend && npm test` — All frontend component tests pass
4. `make test-e2e` — Full E2E suite including transfer with adjustment scenario
5. `make run-demo` — Demo mode starts, DataSeeder creates demo transfer with adjustment (fromAmount=50, adjustment=5, toAmount=55). Verify demo user sees the transfer in the unified activity list.
6. Manual: Create Bank + CC accounts → Transfer with fromAmount=95, adjustment=5 → Verify bank balance -= 95, CC debt -= 100, toAmount=100 displayed
7. Manual: Verify unified activity list (`GET /activity`) shows both transactions and transfers, sorted by date, with correct pagination and type filtering
