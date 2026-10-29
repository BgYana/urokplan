--liquibase formatted sql

--changeset urokplan:relationships-001-relationships
CREATE SCHEMA IF NOT EXISTS relationships;

CREATE TABLE relationships.connection_requests (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    owner_account_id UUID NOT NULL,
    student_account_id UUID NOT NULL,
    requester_account_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP NULL,
    decided_by UUID NULL,
    CONSTRAINT chk_relationship_requests_type
        CHECK (type IN ('LEARNING', 'GUARDIAN')),
    CONSTRAINT chk_relationship_requests_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT chk_relationship_requests_distinct_participants
        CHECK (owner_account_id <> student_account_id),
    CONSTRAINT chk_relationship_requests_requester
        CHECK (requester_account_id IN (owner_account_id, student_account_id))
);

CREATE UNIQUE INDEX uq_relationship_requests_pending_pair
    ON relationships.connection_requests(type, owner_account_id, student_account_id)
    WHERE status = 'PENDING';

CREATE INDEX ix_relationship_requests_owner
    ON relationships.connection_requests(owner_account_id);

CREATE INDEX ix_relationship_requests_student
    ON relationships.connection_requests(student_account_id);

CREATE TABLE relationships.connections (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    owner_account_id UUID NOT NULL,
    student_account_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    terminated_at TIMESTAMP NULL,
    terminated_by UUID NULL,
    CONSTRAINT chk_relationship_connections_type
        CHECK (type IN ('LEARNING', 'GUARDIAN')),
    CONSTRAINT chk_relationship_connections_status
        CHECK (status IN ('ACTIVE', 'TERMINATED')),
    CONSTRAINT chk_relationship_connections_distinct_participants
        CHECK (owner_account_id <> student_account_id)
);

CREATE UNIQUE INDEX uq_relationship_connections_active_pair
    ON relationships.connections(type, owner_account_id, student_account_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_relationship_connections_owner
    ON relationships.connections(owner_account_id);

CREATE INDEX ix_relationship_connections_student
    ON relationships.connections(student_account_id);

--rollback DROP SCHEMA relationships CASCADE;
