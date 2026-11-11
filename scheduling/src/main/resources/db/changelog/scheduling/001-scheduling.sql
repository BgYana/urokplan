--liquibase formatted sql

--changeset urokplan:scheduling-001-scheduling
CREATE SCHEMA IF NOT EXISTS scheduling;

CREATE TABLE scheduling.lesson_series (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    teacher_account_id UUID NOT NULL,
    group_id UUID NULL,
    local_time TIME NOT NULL,
    zone_id VARCHAR(80) NOT NULL,
    days_of_week VARCHAR(80) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    call_link VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_lesson_series_period CHECK (end_date >= start_date),
    CONSTRAINT chk_lesson_series_duration CHECK (duration_minutes BETWEEN 1 AND 1440),
    CONSTRAINT chk_lesson_series_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE TABLE scheduling.lessons (
    id UUID PRIMARY KEY,
    series_id UUID NULL REFERENCES scheduling.lesson_series(id),
    course_id UUID NOT NULL,
    teacher_account_id UUID NOT NULL,
    group_id UUID NULL,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    call_link VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_lessons_period CHECK (ends_at > starts_at),
    CONSTRAINT chk_lessons_status CHECK (status IN ('SCHEDULED', 'CANCELLED'))
);

CREATE INDEX ix_lessons_teacher_time
    ON scheduling.lessons(teacher_account_id, starts_at, ends_at);
CREATE INDEX ix_lessons_course_time
    ON scheduling.lessons(course_id, starts_at);
CREATE INDEX ix_lessons_series_time
    ON scheduling.lessons(series_id, starts_at);

CREATE TABLE scheduling.lesson_participants (
    lesson_id UUID NOT NULL REFERENCES scheduling.lessons(id),
    student_account_id UUID NOT NULL,
    price_minor BIGINT NOT NULL,
    attendance VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    attendance_updated_at TIMESTAMP NULL,
    attendance_updated_by UUID NULL,
    CONSTRAINT pk_lesson_participants PRIMARY KEY (lesson_id, student_account_id),
    CONSTRAINT chk_lesson_participants_price CHECK (price_minor > 0),
    CONSTRAINT chk_lesson_participants_attendance
        CHECK (attendance IN ('UNKNOWN', 'PRESENT', 'EXCUSED_ABSENCE', 'UNEXCUSED_ABSENCE'))
);

CREATE INDEX ix_lesson_participants_student
    ON scheduling.lesson_participants(student_account_id, lesson_id);

CREATE TABLE scheduling.attendance_history (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL,
    student_account_id UUID NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    reason VARCHAR(500) NULL
);

CREATE INDEX ix_attendance_history_participant
    ON scheduling.attendance_history(lesson_id, student_account_id, changed_at);

CREATE TABLE scheduling.schedule_locks (
    account_id UUID PRIMARY KEY
);

--rollback DROP SCHEMA scheduling CASCADE;
