--liquibase formatted sql

--changeset urokplan:database-test-001
CREATE TABLE migration_probe (
    id INTEGER PRIMARY KEY
);

--rollback DROP TABLE migration_probe;
