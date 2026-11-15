-- Full-access API tokens: one token per user per scope

ALTER TABLE api_tokens ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'READ_ONLY'
    CHECK (scope IN ('READ_ONLY', 'FULL_ACCESS'));
ALTER TABLE api_tokens DROP CONSTRAINT api_tokens_user_id_key;
ALTER TABLE api_tokens ADD CONSTRAINT api_tokens_user_id_scope_key UNIQUE (user_id, scope);
