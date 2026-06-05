-- Initial schema for Budget Tracker (merged V1 + V2)

-- Enums
CREATE TYPE account_type AS ENUM ('CREDIT_CARD', 'CASH', 'BANK', 'FRIEND_LENDING');
CREATE TYPE transaction_type AS ENUM ('INCOME', 'EXPENSE', 'LEND', 'BORROW');

-- Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Accounts Table
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    type account_type NOT NULL,
    initial_balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    credit_limit DECIMAL(19, 4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Labels Table
CREATE TABLE labels (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Categories Table
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    icon TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Transactions Table (multi-label: no label_id column, uses transaction_labels join table)
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    type transaction_type NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    description TEXT,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Transfers Table (multi-label: no label_id column, uses transfer_labels join table)
CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    from_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    to_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    from_amount DECIMAL(19, 4) NOT NULL,
    adjustment DECIMAL(19, 4) NOT NULL DEFAULT 0,
    to_amount DECIMAL(19, 4) NOT NULL,
    description TEXT,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Multi-label join tables
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

-- User Preferences Table
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    default_account_id UUID,
    default_transaction_type VARCHAR(20),
    default_category_id UUID,
    default_label_id UUID,
    currency_symbol VARCHAR(10) DEFAULT '₹',
    auto_backup_enabled BOOLEAN DEFAULT FALSE,
    auto_backup_frequency VARCHAR(20),
    auto_backup_format VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Backup Records Table
CREATE TABLE backup_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    format VARCHAR(20) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Activity View (unified transaction + transfer listing, no label_id)
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

-- Indexes
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_labels_user_id ON labels(user_id);
CREATE INDEX idx_categories_user_id ON categories(user_id);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transfers_user_id ON transfers(user_id);
CREATE INDEX idx_transfers_from_account_id ON transfers(from_account_id);
CREATE INDEX idx_transfers_to_account_id ON transfers(to_account_id);
CREATE INDEX idx_transfers_date ON transfers(transaction_date);
CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
CREATE INDEX idx_backup_records_user_id ON backup_records(user_id);
CREATE INDEX idx_transaction_labels_transaction_id ON transaction_labels(transaction_id);
CREATE INDEX idx_transaction_labels_label_id ON transaction_labels(label_id);
CREATE INDEX idx_transfer_labels_transfer_id ON transfer_labels(transfer_id);
CREATE INDEX idx_transfer_labels_label_id ON transfer_labels(label_id);
