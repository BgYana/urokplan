--liquibase formatted sql

--changeset urokplan:learning-001-learning
CREATE SCHEMA IF NOT EXISTS learning;

CREATE TABLE learning.assignments (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL,
    teacher_account_id UUID NOT NULL,
    text TEXT NOT NULL,
    deadline TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_learning_assignments_lesson ON learning.assignments(lesson_id, created_at);

CREATE TABLE learning.assignment_links (
    assignment_id UUID NOT NULL REFERENCES learning.assignments(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    url VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_learning_assignment_links PRIMARY KEY (assignment_id, position)
);

CREATE TABLE learning.assignment_targets (
    assignment_id UUID NOT NULL REFERENCES learning.assignments(id) ON DELETE CASCADE,
    student_account_id UUID NOT NULL,
    CONSTRAINT pk_learning_assignment_targets PRIMARY KEY (assignment_id, student_account_id)
);

CREATE INDEX ix_learning_assignment_targets_student
    ON learning.assignment_targets(student_account_id, assignment_id);

CREATE TABLE learning.submission_attempts (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES learning.assignments(id) ON DELETE CASCADE,
    student_account_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    text TEXT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP NULL,
    reviewer_account_id UUID NULL,
    review_comment TEXT NULL,
    score INTEGER NULL,
    reviewed_at TIMESTAMP NULL,
    CONSTRAINT uq_learning_submission_attempt UNIQUE (assignment_id, student_account_id, attempt_number),
    CONSTRAINT chk_learning_submission_number CHECK (attempt_number > 0),
    CONSTRAINT chk_learning_submission_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REVISION_REQUESTED', 'ACCEPTED')),
    CONSTRAINT chk_learning_submission_score CHECK (score IS NULL OR score BETWEEN 0 AND 10)
);

CREATE INDEX ix_learning_submissions_assignment_student
    ON learning.submission_attempts(assignment_id, student_account_id, attempt_number);

CREATE TABLE learning.submission_links (
    submission_id UUID NOT NULL REFERENCES learning.submission_attempts(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    url VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_learning_submission_links PRIMARY KEY (submission_id, position)
);

CREATE TABLE learning.assessments (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL,
    course_id UUID NOT NULL,
    student_account_id UUID NOT NULL,
    teacher_account_id UUID NOT NULL,
    version INTEGER NOT NULL,
    score INTEGER NOT NULL,
    comment TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_learning_assessment_version UNIQUE (lesson_id, student_account_id, version),
    CONSTRAINT chk_learning_assessment_version CHECK (version > 0),
    CONSTRAINT chk_learning_assessment_score CHECK (score BETWEEN 0 AND 10)
);

CREATE INDEX ix_learning_assessments_student_course
    ON learning.assessments(student_account_id, course_id, lesson_id, version);

CREATE TABLE learning.feedback (
    lesson_id UUID NOT NULL,
    student_account_id UUID NOT NULL,
    mood INTEGER NOT NULL,
    difficulty INTEGER NOT NULL,
    comment TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_learning_feedback PRIMARY KEY (lesson_id, student_account_id),
    CONSTRAINT chk_learning_feedback_mood CHECK (mood BETWEEN 1 AND 5),
    CONSTRAINT chk_learning_feedback_difficulty CHECK (difficulty BETWEEN 1 AND 5)
);

--rollback DROP SCHEMA learning CASCADE;
