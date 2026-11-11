--liquibase formatted sql

--changeset urokplan:identity-003-display-name
ALTER TABLE identity.accounts
    ADD COLUMN display_name VARCHAR(100);

UPDATE identity.accounts
SET display_name = login;

ALTER TABLE identity.accounts
    ALTER COLUMN display_name SET NOT NULL;

--rollback ALTER TABLE identity.accounts DROP COLUMN display_name;
