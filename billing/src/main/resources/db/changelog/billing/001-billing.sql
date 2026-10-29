--liquibase formatted sql

--changeset urokplan:billing-001-billing
CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.balances (
    course_id UUID NOT NULL,
    student_account_id UUID NOT NULL,
    available_units INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_billing_balances PRIMARY KEY (course_id, student_account_id),
    CONSTRAINT chk_billing_balances_non_negative CHECK (available_units >= 0)
);

CREATE TABLE billing.payments (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    student_account_id UUID NOT NULL,
    payer_account_id UUID NOT NULL,
    units INTEGER NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    amount_minor BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_name VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(200) NOT NULL,
    confirmation_url VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    failure_code VARCHAR(100) NULL,
    CONSTRAINT chk_billing_payments_units CHECK (units > 0),
    CONSTRAINT chk_billing_payments_prices CHECK (unit_price_minor > 0 AND amount_minor > 0),
    CONSTRAINT chk_billing_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_billing_payments_provider_id
    ON billing.payments(provider_name, provider_payment_id);

CREATE INDEX ix_billing_payments_student_course
    ON billing.payments(student_account_id, course_id, created_at);

CREATE TABLE billing.ledger_entries (
    id UUID PRIMARY KEY,
    sequence_number BIGSERIAL NOT NULL UNIQUE,
    course_id UUID NOT NULL,
    student_account_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    units_delta INTEGER NOT NULL,
    payment_id UUID NULL,
    lesson_id UUID NULL,
    created_at TIMESTAMP NOT NULL,
    actor_account_id UUID NULL,
    reason VARCHAR(500) NULL,
    CONSTRAINT chk_billing_ledger_type CHECK (type IN ('TOP_UP', 'CONSUMPTION', 'REFUND')),
    CONSTRAINT chk_billing_ledger_delta CHECK (units_delta <> 0)
);

CREATE UNIQUE INDEX uq_billing_ledger_payment_top_up
    ON billing.ledger_entries(payment_id)
    WHERE type = 'TOP_UP';

CREATE INDEX ix_billing_ledger_student_course
    ON billing.ledger_entries(student_account_id, course_id, created_at);

CREATE TABLE billing.lesson_charges (
    lesson_id UUID NOT NULL,
    student_account_id UUID NOT NULL,
    course_id UUID NOT NULL,
    lesson_starts_at TIMESTAMP NOT NULL,
    lesson_ends_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    covered_at TIMESTAMP NULL,
    waived_at TIMESTAMP NULL,
    CONSTRAINT pk_billing_lesson_charges PRIMARY KEY (lesson_id, student_account_id),
    CONSTRAINT chk_billing_lesson_charges_status CHECK (status IN ('OPEN', 'COVERED', 'WAIVED'))
);

CREATE INDEX ix_billing_open_charges
    ON billing.lesson_charges(course_id, student_account_id, lesson_starts_at)
    WHERE status = 'OPEN';

--rollback DROP SCHEMA billing CASCADE;
