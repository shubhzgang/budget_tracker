-- Expenditure period totals: stored WEEK / MONTH spend aggregates (EXPENSE + LEND),
-- maintained eagerly on every transaction create/update/delete. Periods are computed
-- in the app timezone (Asia/Kolkata).

CREATE TABLE expenditure_period_totals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_type VARCHAR(10) NOT NULL,
    period_key VARCHAR(9) NOT NULL,
    total DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_expenditure_period_totals UNIQUE (user_id, period_type, period_key)
);

CREATE INDEX idx_expenditure_period_totals_user_id ON expenditure_period_totals(user_id);

-- Backfill from existing transactions so historical periods are available immediately.

INSERT INTO expenditure_period_totals (id, user_id, period_type, period_key, total)
SELECT gen_random_uuid(), t.user_id, 'WEEK',
       TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'IYYY-"W"IW'),
       SUM(t.amount)
FROM transactions t
WHERE t.type IN ('EXPENSE', 'LEND')
GROUP BY t.user_id, TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'IYYY-"W"IW');

INSERT INTO expenditure_period_totals (id, user_id, period_type, period_key, total)
SELECT gen_random_uuid(), t.user_id, 'MONTH',
       TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM'),
       SUM(t.amount)
FROM transactions t
WHERE t.type IN ('EXPENSE', 'LEND')
GROUP BY t.user_id, TO_CHAR(t.transaction_date AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM');
