--liquibase formatted sql

--changeset urokplan:identity-004-billing-time-zone
ALTER TABLE identity.accounts
    ADD COLUMN billing_time_zone VARCHAR(80) NOT NULL DEFAULT 'UTC';

ALTER TABLE identity.accounts
    ALTER COLUMN billing_time_zone DROP DEFAULT;

--rollback ALTER TABLE identity.accounts DROP COLUMN billing_time_zone;
