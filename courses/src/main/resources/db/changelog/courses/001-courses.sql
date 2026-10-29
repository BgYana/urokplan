--liquibase formatted sql

--changeset urokplan:courses-001-courses
CREATE SCHEMA IF NOT EXISTS courses;

CREATE TABLE courses.courses (
    id UUID PRIMARY KEY,
    teacher_account_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    subject VARCHAR(100) NOT NULL,
    format VARCHAR(20) NOT NULL,
    call_link VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_courses_format CHECK (format IN ('INDIVIDUAL', 'GROUP'))
);

CREATE INDEX ix_courses_teacher ON courses.courses(teacher_account_id);

CREATE TABLE courses.enrollments (
    course_id UUID NOT NULL REFERENCES courses.courses(id),
    student_account_id UUID NOT NULL,
    price_minor BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    enrolled_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NULL,
    CONSTRAINT pk_course_enrollments PRIMARY KEY (course_id, student_account_id),
    CONSTRAINT chk_course_enrollments_price CHECK (price_minor > 0),
    CONSTRAINT chk_course_enrollments_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_course_enrollments_student
    ON courses.enrollments(student_account_id);

CREATE TABLE courses.groups (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses.courses(id),
    name VARCHAR(100) NOT NULL,
    call_link VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_groups_course ON courses.groups(course_id);

CREATE TABLE courses.group_memberships (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES courses.groups(id),
    course_id UUID NOT NULL REFERENCES courses.courses(id),
    student_account_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    left_at TIMESTAMP NULL,
    CONSTRAINT chk_group_memberships_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_group_memberships_active_course_student
    ON courses.group_memberships(course_id, student_account_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_group_memberships_group ON courses.group_memberships(group_id);
CREATE INDEX ix_group_memberships_student ON courses.group_memberships(student_account_id);

--rollback DROP SCHEMA courses CASCADE;
