package urokplan.learning.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import urokplan.learning.domain.SubmissionStatus

internal object AssignmentsTable : Table("learning.assignments") {
    val id = uuid("id")
    val lessonId = uuid("lesson_id")
    val teacherAccountId = uuid("teacher_account_id")
    val text = text("text")
    val deadline = timestamp("deadline").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object AssignmentLinksTable : Table("learning.assignment_links") {
    val assignmentId = uuid("assignment_id")
    val position = integer("position")
    val url = varchar("url", 2_000)
    override val primaryKey = PrimaryKey(assignmentId, position)
}

internal object AssignmentTargetsTable : Table("learning.assignment_targets") {
    val assignmentId = uuid("assignment_id")
    val studentAccountId = uuid("student_account_id")
    override val primaryKey = PrimaryKey(assignmentId, studentAccountId)
}

internal object SubmissionAttemptsTable : Table("learning.submission_attempts") {
    val id = uuid("id")
    val assignmentId = uuid("assignment_id")
    val studentAccountId = uuid("student_account_id")
    val attemptNumber = integer("attempt_number")
    val text = text("text").nullable()
    val status = enumerationByName<SubmissionStatus>("status", 30)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val submittedAt = timestamp("submitted_at").nullable()
    val reviewerAccountId = uuid("reviewer_account_id").nullable()
    val reviewComment = text("review_comment").nullable()
    val score = integer("score").nullable()
    val reviewedAt = timestamp("reviewed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object SubmissionLinksTable : Table("learning.submission_links") {
    val submissionId = uuid("submission_id")
    val position = integer("position")
    val url = varchar("url", 2_000)
    override val primaryKey = PrimaryKey(submissionId, position)
}

internal object AssessmentsTable : Table("learning.assessments") {
    val id = uuid("id")
    val lessonId = uuid("lesson_id")
    val courseId = uuid("course_id")
    val studentAccountId = uuid("student_account_id")
    val teacherAccountId = uuid("teacher_account_id")
    val version = integer("version")
    val score = integer("score")
    val comment = text("comment").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object FeedbackTable : Table("learning.feedback") {
    val lessonId = uuid("lesson_id")
    val studentAccountId = uuid("student_account_id")
    val mood = integer("mood")
    val difficulty = integer("difficulty")
    val comment = text("comment").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(lessonId, studentAccountId)
}
