-- Per-label expenditure breakdown on the dashboard period cards.
-- Add a nullable label_name column to expenditure_period_totals:
--   label_name IS NULL  -> overall total for the period (existing behavior)
--   label_name = '__UNLABELLED__' -> aggregate for transactions with no labels
--   label_name = '<name>' -> aggregate for a specific label
-- Aggregate rows are snapshots (shortened label name at write time). They are
-- rebuilt wholesale by recomputeForUser whenever labels are renamed/deleted.

-- Add nullable label_name column; NULL = overall total (existing rows).
ALTER TABLE expenditure_period_totals ADD COLUMN label_name VARCHAR(255);

-- Replace the old unique constraint with a unique index using COALESCE so that
-- NULL label_name rows collide correctly (PostgreSQL unique constraints treat
-- NULLs as distinct, which we don't want here).
ALTER TABLE expenditure_period_totals
    DROP CONSTRAINT uq_expenditure_period_totals;

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
