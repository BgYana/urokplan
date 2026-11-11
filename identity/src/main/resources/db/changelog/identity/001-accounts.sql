--liquibase formatted sql

--changeset urokplan:identity-001-accounts
CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.accounts (
    id UUID PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    login VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_identity_accounts_public_id UNIQUE (public_id),
    CONSTRAINT uq_identity_accounts_login UNIQUE (login)
);

CREATE TABLE identity.account_roles (
    account_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    CONSTRAINT pk_identity_account_roles PRIMARY KEY (account_id, role),
    CONSTRAINT fk_identity_account_roles_account
        FOREIGN KEY (account_id)
        REFERENCES identity.accounts(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_identity_account_roles_role
        CHECK (role IN ('STUDENT', 'TEACHER', 'PARENT'))
);

--rollback DROP SCHEMA identity CASCADE;
