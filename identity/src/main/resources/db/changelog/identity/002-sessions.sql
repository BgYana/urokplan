--liquibase formatted sql

--changeset urokplan:identity-002-sessions
CREATE TABLE identity.sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    CONSTRAINT uq_identity_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_identity_sessions_account
        FOREIGN KEY (account_id)
        REFERENCES identity.accounts(id)
        ON DELETE CASCADE
);

CREATE INDEX ix_identity_sessions_account_id
    ON identity.sessions(account_id);

CREATE INDEX ix_identity_sessions_expires_at
    ON identity.sessions(expires_at);

--rollback DROP TABLE identity.sessions;
