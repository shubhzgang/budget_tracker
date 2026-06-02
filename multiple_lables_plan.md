# Multi-Label Support — Implementation Plan

## Context

Currently every `Transaction` and `Transfer` has a single optional `label` via `@ManyToOne`. The user wants to support multiple labels per item (e.g., tagging a transaction as both "NEEDS" and "WANTS"). Since the app is not deployed, no data migration is needed — we simply change the schema and code.

---

## Step 1: Flyway V2 Schema Change

**File:** `src/main/resources/db/migration/V2__multi_label_support.sql` (NEW)

Create join tables, drop `label_id` columns, recreate `activity_view` with `array_agg`:

```sql
-- 1. Create join tables
CREATE TABLE transaction_labels (
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, label_id)
);

CREATE TABLE transfer_labels (
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (transfer_id, label_id)
);

-- 2. Drop old FK columns
ALTER TABLE transactions DROP COLUMN label_id;
ALTER TABLE transfers DROP COLUMN label_id;

-- 3. Create indexes
CREATE INDEX idx_transaction_labels_transaction_id ON transaction_labels(transaction_id);
CREATE INDEX idx_transaction_labels_label_id ON transaction_labels(label_id);
CREATE INDEX idx_transfer_labels_transfer_id ON transfer_labels(transfer_id);
CREATE INDEX idx_transfer_labels_label_id ON transfer_labels(label_id);

-- 4. Recreate activity_view with array_agg for label_ids
DROP VIEW IF EXISTS activity_view;
CREATE VIEW activity_view AS
SELECT
    t.id, 'TRANSACTION' AS kind, t.user_id, t.account_id,
    NULL::uuid AS to_account_id, t.category_id,
    COALESCE((SELECT array_agg(tl.label_id) FROM transaction_labels tl WHERE tl.transaction_id = t.id), ARRAY[]::uuid[]) AS label_ids,
    t.amount, t.type::text AS type,
    NULL::decimal(19,4) AS from_amount, NULL::decimal(19,4) AS to_amount, NULL::decimal(19,4) AS adjustment,
    t.description, t.transaction_date, t.created_at
FROM transactions t
UNION ALL
SELECT
    tr.id, 'TRANSFER' AS kind, tr.user_id, tr.from_account_id AS account_id,
    tr.to_account_id, tr.category_id,
    COALESCE((SELECT array_agg(trl.label_id) FROM transfer_labels trl WHERE trl.transfer_id = tr.id), ARRAY[]::uuid[]) AS label_ids,
    NULL::decimal(19,4) AS amount, 'TRANSFER' AS type,
    tr.from_amount, tr.to_amount, tr.adjustment,
    tr.description, tr.transaction_date, tr.created_at
FROM transfers tr;
```

---

## Step 2: JPA Entities

### Transaction.java
Replace `@ManyToOne Label label` with:
```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(name = "transaction_labels", joinColumns = @JoinColumn(name = "transaction_id"), inverseJoinColumns = @JoinColumn(name = "label_id"))
private Set<Label> labels = new HashSet<>();
```
Add `import java.util.Set; import java.util.HashSet;`

### Transfer.java
Same pattern with `transfer_labels` join table.

### ActivityItem.java
Completely **delete** the `@ManyToOne Label label` field and its `@JoinColumn(name = "label_id")` annotation. After Flyway V2, the column no longer exists. Then, add the new transient field:
```java
@Transient
private Set<Label> labels = new HashSet<>();
```
JPA cannot map `uuid[]` from the view via `@ManyToMany`, so labels are populated by the service layer. This is not a workaround, but the designed approach for this entity.

### Label.java
Override Lombok `@Setter` with custom setter for pipe validation:
```java
public void setName(String name) {
    if (name != null && name.contains("|")) {
        throw new IllegalArgumentException("Label name cannot contain the pipe character '|'");
    }
    this.name = name;
}
```

---

## Step 3: TestDatabaseInitializer

**File:** `src/test/java/.../TestDatabaseInitializer.java`

- Create `transaction_labels` and `transfer_labels` tables in H2
- Recreate `activity_view` without `label_id` column (H2 does not support `array_agg`)

**Note on H2/PostgreSQL View Split:**
- Flyway V2: `activity_view` exposes `label_ids uuid[]` — but JPA does not map it.
- TestDatabaseInitializer: `activity_view` has no label column at all.
- `ActivityItem` is `@Transient` for labels because the service layer populates them — not as a workaround, but as the designed approach.

---

## Step 4: Repositories

### TransactionRepository.java
- `LEFT JOIN FETCH t.label` → `LEFT JOIN FETCH t.labels` in all queries
- Search by label: `LEFT JOIN t.labels lbl` and `LOWER(lbl.name) LIKE ...`
- `findAccountTransactions` and `searchTransactions` already have `DISTINCT` — **`findAllByUserId` does NOT** and must be updated to `SELECT DISTINCT t FROM Transaction t LEFT JOIN FETCH ...` to avoid duplicate rows when a transaction has multiple labels

### TransferRepository.java
- Same pattern as TransactionRepository
- **`findAllByUserId` does NOT have `DISTINCT`** and must be updated to `SELECT DISTINCT t FROM Transfer t LEFT JOIN FETCH ...` to avoid duplicate rows when a transfer has multiple labels

### ActivityRepository.java
- **Non-search queries** (`findAllByUserId`, `findByAccountId`): Remove `LEFT JOIN FETCH a.label` entirely — the `label_id` column no longer exists on the view
- **Search query — label filter regression**: The current `searchActivity` query filters by label name via `LOWER(a.label.name) LIKE ...`. Since `activity_view` no longer has `label_id`, JPQL cannot JOIN to labels. A native query joining `activity_view` + `transaction_labels`/`transfer_labels` + `labels` with `GROUP BY` on all non-aggregated columns is brittle. **Drop the label from the JPQL search condition entirely and filter in the service layer.** Caveat: because filtering happens after pagination, total page counts will NOT reflect the label filter — items that don't match the label will simply be removed from the page content, potentially yielding fewer results than `totalElements` suggests. This is an acceptable UX tradeoff for simplicity.
- **Label batch-lookup**: Add a method to fetch label associations for a list of activity IDs:
  ```java
  @Query(value = "SELECT tl.transaction_id AS activity_id, l.* FROM transaction_labels tl JOIN labels l ON l.id = tl.label_id WHERE tl.transaction_id IN :ids", nativeQuery = true)
  List<Object[]> findLabelsForTransactions(@Param("ids") List<UUID> ids);

  @Query(value = "SELECT trl.transfer_id AS activity_id, l.* FROM transfer_labels trl JOIN labels l ON l.id = trl.label_id WHERE trl.transfer_id IN :ids", nativeQuery = true)
  List<Object[]> findLabelsForTransfers(@Param("ids") List<UUID> ids);
  ```

---

## Step 5: DTOs

### TransferRequest.java
**Explicitly delete** `UUID labelId` and replace it with `List<UUID> labelIds`. Do not let them coexist; there is no need for backward compatibility shims since the app isn't deployed.

### TransferResponse.java
`Label label` → `Set<Label> labels`

### ActivityResponse.java
`Label label` → `Set<Label> labels`

---

## Step 6: Services

### TransactionService.java
- `updateTransaction` (line 94): `existing.setLabel(...)` → `existing.setLabels(transactionDetails.getLabels())`
- Spring Boot deserializes `{ "labels": [{ "id": "..." }] }` into `Set<Label>` via JPA proxy references — works automatically

### TransferService.java
In `createTransfer` and `updateTransfer`, replace single-label lookup with a batch fetch to avoid N+1 queries:
```java
Set<Label> labels = new HashSet<>();
if (request.getLabelIds() != null && !request.getLabelIds().isEmpty()) {
    List<Label> fetchedLabels = labelRepository.findAllById(request.getLabelIds());
    for (Label label : fetchedLabels) {
        if (!label.getUserId().equals(userId)) {
            throw new RuntimeException("Label not found or access denied");
        }
        labels.add(label);
    }
    if (labels.size() != request.getLabelIds().size()) {
        throw new RuntimeException("One or more labels not found");
    }
}
transfer.setLabels(labels);
```

### ActivityService.java
- `mapToResponse`: `response.setLabel(item.getLabel())` → `response.setLabels(item.getLabels())`
- In `getActivity()`: after fetching the paginated `ActivityItem` results and populating labels, **post-filter by label name** if `searchTerm` is non-empty — iterate the responses and remove any item whose labels don't contain a match against the search term. Since labels are populated before filtering, the heuristic is: if none of an item's labels match, and the description/category don't match, remove it from the page content. Note: this changes the content size but NOT `totalElements`/`totalPages` since pagination ran before the label filter.
- Add `populateLabels()` — after fetching `ActivityItem` results:
  1. Separate IDs by kind (TRANSACTION vs TRANSFER)
  2. Call the repository batch-lookup methods
  3. Map the `Object[]` rows back to `Label` entities
  4. Group results by `activity_id`, set on each item's `labels` field
  *(Note: This is the most code-heavy single change in the backend, taking ~50-60 lines of logic to split, fetch, map, and group)*

### LabelService.java
Add pipe validation in `createLabel` and `updateLabel`:
```java
private void validateLabelName(String name) {
    if (name != null && name.contains("|")) {
        throw new IllegalArgumentException("Label name cannot contain the pipe character '|'");
    }
}
```

### BackupService.java
**SQL Export:**
- Remove `label_id` from `INSERT INTO transactions` and `INSERT INTO transfers` column list
- After each transaction/transfer INSERT, add join-table inserts:
  ```java
  for (Label label : transaction.getLabels()) {
      sql.append(String.format("INSERT INTO transaction_labels (transaction_id, label_id) VALUES ('%s', '%s');\n",
          transaction.getId(), label.getId()));
  }
  ```

**SQL Import Assumption:**
- `importFromSql` runs raw INSERTs. The generated V2 backups will contain `INSERT INTO transaction_labels`. These backups must only be imported into a database where the Flyway V2 schema has already been applied. (This is acceptable since the app is not yet deployed).

**CSV Export:**
- Header: `"Label"` → `"Labels"`
- Value: `t.getLabel()?.getName()` → `t.getLabels().stream().map(Label::getName).collect(Collectors.joining("|"))`

**CSV Import:**
- Parse `nextLine[6]` by splitting on `|`, look up/create each label, add to `Set<Label>`

---

## Step 7: Controllers

### TransferController.java
`mapToResponse`: `response.setLabel(transfer.getLabel())` → `response.setLabels(transfer.getLabels())`

### ActivityController.java
No direct changes — `ActivityService.mapToResponse` handles the `labels` field

### TransactionController.java
No code changes needed. It serializes the `Transaction` entity directly. With Lombok `@Getter`, the JSON response automatically changes from `{ "label": {...} }` to `{ "labels": [...] }`. Similarly, POST/PUT deserializes `labels: [{id: "..."}]` into `Set<Label>` via JPA.

---

## Step 8: DataSeeder

- Inject `LabelRepository` (note: `initializeDefaultLabels()` already runs early, so labels exist)
- For demo transactions, assign labels on the entity:
  ```java
  Label needs = labelRepository.findAllByUserId(DEMO_USER_ID).stream()
      .filter(l -> l.getName().equals("NEEDS")).findFirst().orElse(null);
  if (needs != null) foodExpense.setLabels(Set.of(needs));
  ```
- For the demo transfer, `labelIds` must be set on the `TransferRequest` **before** calling `createTransfer()`, not on the returned entity after.

---

## Step 9: Frontend Types

### transaction.ts
- `Transaction`: `labelId?: string; label?: Label` → `labelIds?: string[]; labels?: Label[]`
- `CreateTransactionRequest`: `labelId?: string` → `labelIds?: string[]`

### transfer.ts
- `Transfer`: `labelId?: string; label?: Label` → `labelIds?: string[]; labels?: Label[]`
- `CreateTransferRequest`: `labelId?: string` → `labelIds?: string[]`

### activity.ts
- `ActivityItem`: `label?: Label` → `labels?: Label[]`

---

## Step 10: Frontend Components

### TransactionForm.tsx
- Form state: `labelId: string` → `labelIds: string[]`
- Edit mode init: `initialData.label?.id` → `initialData.labels?.map(l => l.id) || []`
- Create mode init: `[preferences?.defaultLabelId]` (filter nulls)
- Replace `<select>` with a multi-select dropdown (checkboxes in a dropdown panel)
- **Payload Shaping:** Detect transfer mode via `formData.type === 'TRANSFER'`. Emit `labelIds: string[]` for transfers, and `labels: [{id}]` for transactions. Ensure the form *always* emits the final API-ready shape.

### TransactionList.tsx
- Replace single label badge with map over `item.labels`:
  ```tsx
  {item.labels && item.labels.length > 0 && item.labels.map((label) => (
    <React.Fragment key={label.id}>
      <span className="text-[10px] text-muted-foreground uppercase font-bold">•</span>
      <span className="px-1.5 py-0.5 bg-accent text-accent-foreground text-[10px] rounded-full font-medium">
        {label.name}
      </span>
    </React.Fragment>
  ))}
  ```

### Analytics.tsx
- Distribute amount across all labels in "Spending by Label" chart:
  ```tsx
  if (!t.labels || t.labels.length === 0) {
    data['Unlabeled'] = (data['Unlabeled'] || 0) + Math.abs(t.amount);
  } else {
    t.labels.forEach(label => {
      data[label.name] = (data[label.name] || 0) + Math.abs(t.amount);
    });
  }
  ```

### Layout.tsx
- Remove all payload reshaping from `handleCreateTransaction`. Since `TransactionForm.tsx` will now emit the correctly shaped payload, simply pass `data` directly to `apiClient.post()`.

### LabelManager.tsx
- Add pipe character validation on form submit:
  ```tsx
  if (name.includes('|')) { addToast("Label name cannot contain '|'", 'error'); return; }
  ```

### Transactions.tsx
- No changes needed to `handleSaveTransaction`. It already passes form data directly to PUT, which will now work correctly since `TransactionForm` emits the final API-ready shape.

---

## Step 11: Frontend Mocks

### handlers.ts
- Add `labels: [{ id: 'l1', name: 'Personal', isDefault: true }]` to mock transaction and activity items
- Remove `label` field from mocks

---

## Step 12: Backend Tests

### Update existing tests
- `TransactionServiceTest`: `buildTransaction()` helper uses `setLabels` instead of `setLabel`
- `TransferServiceTest`: use `setLabelIds(List.of(...))` instead of `setLabelId`
- `LabelServiceTest`: add `createLabel_withPipeCharacter_shouldThrow`
- `BackupServiceTest`: add `exportToSql_withMultiLabel_shouldIncludeJoinTableInserts`
- `TransactionControllerTest`: `shouldReturnTransactionWithLabelsArray`
- `TransferControllerTest`: `shouldReturnTransferWithLabelsArray`
- `ActivityControllerTest`: `shouldReturnActivityWithLabelsArray`
- `TransactionRepositoryTest`: `shouldSaveTransactionWithMultipleLabels`, `shouldSearchByAnyLabelName`
- `TransferRepositoryTest`: same as above
- `TransactionIntegrationTest`: `testTransactionWithMultipleLabels`
- `TransferIntegrationTest`: `testTransferWithMultipleLabels`
- `ActivityIntegrationTest`: `testActivityShowsMultipleLabels`
- `BackupSystemIntegrationTest`: SQL/CSV backup preserves multi-label
- **IMPORTANT for `ActivityItem`:** Verify no existing tests (like `ActivityControllerTest` or `ActivityIntegrationTest`) mock or build `ActivityItem` using `setLabel`. Replace any `item.setLabel(lbl)` with `item.setLabels(Set.of(lbl))`.

### New tests
- `TransferServiceTest`: `createTransfer_withMultipleLabels`, `updateTransfer_shouldReplaceLabels`
- `TransactionServiceTest`: `createTransaction_withMultipleLabels`, `updateTransaction_withEmptyLabels_shouldClearAll`

---

## Step 13: Frontend Tests

- `TransactionForm.test.tsx`: multi-select, pre-fill default label, submit with multiple labels
- `TransactionList.test.tsx`: display multiple label badges, no badges when empty
- `Analytics.test.tsx`: distribute amount across multiple labels
- `LabelManager.test.tsx`: reject label name with pipe character
- `DashboardIntegration.test.tsx`: update mock data with `labels` array

---

## Step 14: E2E Tests

### New: `e2e/tests/multi-label.spec.ts`
- Create transaction with multiple labels, verify badges in list
- Create transfer with multiple labels
- Edit transaction and change labels
- Search transactions by label name
- Reject label name with pipe character

### Modify: `e2e/tests/preferences.spec.ts`
- Verify default label preference still works with multi-select (single default pre-selected)

---

## Verification

1. `./gradlew test` — all unit tests pass
2. `make test-int` — all integration tests pass with PostgreSQL testcontainers
3. `cd frontend && npm test` — all frontend component tests pass
4. `make test-e2e` — all Playwright E2E tests pass including new multi-label spec
5. Manual: Create transaction with 2 labels → verify both badges in list → verify pie chart counts both → export CSV → verify pipe-delimited labels → verify SQL backup includes join table inserts
