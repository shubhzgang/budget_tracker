-- Multi-label support: replace single label_id with join tables

-- 1. Drop old view first (depends on label_id columns)
DROP VIEW IF EXISTS activity_view;

-- 2. Create join tables
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

-- 3. Drop old FK columns
ALTER TABLE transactions DROP COLUMN label_id;
ALTER TABLE transfers DROP COLUMN label_id;

-- 4. Create indexes
CREATE INDEX idx_transaction_labels_transaction_id ON transaction_labels(transaction_id);
CREATE INDEX idx_transaction_labels_label_id ON transaction_labels(label_id);
CREATE INDEX idx_transfer_labels_transfer_id ON transfer_labels(transfer_id);
CREATE INDEX idx_transfer_labels_label_id ON transfer_labels(label_id);

-- 5. Recreate activity_view (labels populated via separate native queries in ActivityService)
CREATE VIEW activity_view AS
SELECT
    t.id, 'TRANSACTION' AS kind, t.user_id, t.account_id,
    NULL::uuid AS to_account_id, t.category_id,
    t.amount, t.type::text AS type,
    NULL::decimal(19,4) AS from_amount, NULL::decimal(19,4) AS to_amount, NULL::decimal(19,4) AS adjustment,
    t.description, t.transaction_date, t.created_at
FROM transactions t
UNION ALL
SELECT
    tr.id, 'TRANSFER' AS kind, tr.user_id, tr.from_account_id AS account_id,
    tr.to_account_id, tr.category_id,
    NULL::decimal(19,4) AS amount, 'TRANSFER' AS type,
    tr.from_amount, tr.to_amount, tr.adjustment,
    tr.description, tr.transaction_date, tr.created_at
FROM transfers tr;
