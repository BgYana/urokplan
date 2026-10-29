package urokplan.learning.infrastructure

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import urokplan.learning.application.LearningRepository
import urokplan.learning.domain.Assessment
import urokplan.learning.domain.Assignment
import urokplan.learning.domain.LessonFeedback
import urokplan.learning.domain.SubmissionAttempt
import java.util.UUID

class ExposedLearningRepository : LearningRepository {
    override fun createAssignment(assignment: Assignment): Assignment {
        AssignmentsTable.insert {
            it[id] = assignment.id
            it[lessonId] = assignment.lessonId
            it[teacherAccountId] = assignment.teacherAccountId
            it[text] = assignment.text
            it[deadline] = assignment.deadline
            it[createdAt] = assignment.createdAt
        }
        assignment.links.forEachIndexed { index, link ->
            AssignmentLinksTable.insert {
                it[assignmentId] = assignment.id
                it[position] = index
                it[url] = link
            }
        }
        assignment.targetStudentIds.forEach { studentId ->
            AssignmentTargetsTable.insert {
                it[assignmentId] = assignment.id
                it[studentAccountId] = studentId
            }
        }
        return assignment
    }

    override fun findAssignment(id: UUID): Assignment? =
        AssignmentsTable.selectAll().where { AssignmentsTable.id eq id }.singleOrNull()?.toAssignment()

    override fun listAssignments(lessonId: UUID?): List<Assignment> {
        val query = if (lessonId == null) {
            AssignmentsTable.selectAll()
        } else {
            AssignmentsTable.selectAll().where { AssignmentsTable.lessonId eq lessonId }
        }
        return query.orderBy(AssignmentsTable.createdAt).map { it.toAssignment() }
    }

    override fun createSubmission(submission: SubmissionAttempt): SubmissionAttempt {
        SubmissionAttemptsTable.insert { writeSubmission(it, submission) }
        replaceSubmissionLinks(submission)
        return submission
    }

    override fun updateSubmission(submission: SubmissionAttempt): SubmissionAttempt {
        SubmissionAttemptsTable.update({ SubmissionAttemptsTable.id eq submission.id }) {
            it[text] = submission.text
            it[status] = submission.status
            it[updatedAt] = submission.updatedAt
            it[submittedAt] = submission.submittedAt
            it[reviewerAccountId] = submission.reviewerAccountId
            it[reviewComment] = submission.reviewComment
            it[score] = submission.score
            it[reviewedAt] = submission.reviewedAt
        }
        replaceSubmissionLinks(submission)
        return submission
    }

    override fun findSubmission(id: UUID): SubmissionAttempt? =
        SubmissionAttemptsTable.selectAll().where { SubmissionAttemptsTable.id eq id }
            .singleOrNull()?.toSubmission()

    override fun listSubmissions(assignmentId: UUID, studentIds: Set<UUID>?): List<SubmissionAttempt> {
        if (studentIds != null && studentIds.isEmpty()) return emptyList()
        val query = SubmissionAttemptsTable.selectAll().where {
            if (studentIds == null) {
                SubmissionAttemptsTable.assignmentId eq assignmentId
            } else {
                (SubmissionAttemptsTable.assignmentId eq assignmentId) and
                    (SubmissionAttemptsTable.studentAccountId inList studentIds)
            }
        }
        return query.orderBy(
            SubmissionAttemptsTable.studentAccountId to SortOrder.ASC,
            SubmissionAttemptsTable.attemptNumber to SortOrder.ASC,
        ).map { it.toSubmission() }
    }

    override fun findLatestSubmission(assignmentId: UUID, studentId: UUID): SubmissionAttempt? =
        SubmissionAttemptsTable.selectAll().where {
            (SubmissionAttemptsTable.assignmentId eq assignmentId) and
                (SubmissionAttemptsTable.studentAccountId eq studentId)
        }.orderBy(SubmissionAttemptsTable.attemptNumber to SortOrder.DESC)
            .limit(1).singleOrNull()?.toSubmission()

    override fun createAssessment(assessment: Assessment): Assessment {
        AssessmentsTable.insert {
            it[id] = assessment.id
            it[lessonId] = assessment.lessonId
            it[courseId] = assessment.courseId
            it[studentAccountId] = assessment.studentAccountId
            it[teacherAccountId] = assessment.teacherAccountId
            it[version] = assessment.version
            it[score] = assessment.score
            it[comment] = assessment.comment
            it[createdAt] = assessment.createdAt
        }
        return assessment
    }

    override fun listAssessments(studentId: UUID, courseId: UUID, lessonId: UUID?): List<Assessment> {
        val query = AssessmentsTable.selectAll().where {
            var condition = (AssessmentsTable.studentAccountId eq studentId) and
                (AssessmentsTable.courseId eq courseId)
            if (lessonId != null) condition = condition and (AssessmentsTable.lessonId eq lessonId)
            condition
        }
        return query.orderBy(
            AssessmentsTable.createdAt to SortOrder.ASC,
            AssessmentsTable.version to SortOrder.ASC,
        ).map { it.toAssessment() }
    }

    override fun upsertFeedback(feedback: LessonFeedback): LessonFeedback {
        FeedbackTable.insertIgnore {
            it[lessonId] = feedback.lessonId
            it[studentAccountId] = feedback.studentAccountId
            it[mood] = feedback.mood
            it[difficulty] = feedback.difficulty
            it[comment] = feedback.comment
            it[createdAt] = feedback.createdAt
            it[updatedAt] = feedback.updatedAt
        }
        FeedbackTable.update({
            (FeedbackTable.lessonId eq feedback.lessonId) and
                (FeedbackTable.studentAccountId eq feedback.studentAccountId)
        }) {
            it[mood] = feedback.mood
            it[difficulty] = feedback.difficulty
            it[comment] = feedback.comment
            it[updatedAt] = feedback.updatedAt
        }
        return feedback
    }

    override fun listFeedback(lessonId: UUID, studentIds: Set<UUID>?): List<LessonFeedback> {
        if (studentIds != null && studentIds.isEmpty()) return emptyList()
        val query = FeedbackTable.selectAll().where {
            if (studentIds == null) {
                FeedbackTable.lessonId eq lessonId
            } else {
                (FeedbackTable.lessonId eq lessonId) and (FeedbackTable.studentAccountId inList studentIds)
            }
        }
        return query.orderBy(FeedbackTable.studentAccountId).map { it.toFeedback() }
    }

    private fun writeSubmission(
        statement: org.jetbrains.exposed.sql.statements.InsertStatement<Number>,
        submission: SubmissionAttempt,
    ) {
        statement[SubmissionAttemptsTable.id] = submission.id
        statement[SubmissionAttemptsTable.assignmentId] = submission.assignmentId
        statement[SubmissionAttemptsTable.studentAccountId] = submission.studentAccountId
        statement[SubmissionAttemptsTable.attemptNumber] = submission.attemptNumber
        statement[SubmissionAttemptsTable.text] = submission.text
        statement[SubmissionAttemptsTable.status] = submission.status
        statement[SubmissionAttemptsTable.createdAt] = submission.createdAt
        statement[SubmissionAttemptsTable.updatedAt] = submission.updatedAt
        statement[SubmissionAttemptsTable.submittedAt] = submission.submittedAt
        statement[SubmissionAttemptsTable.reviewerAccountId] = submission.reviewerAccountId
        statement[SubmissionAttemptsTable.reviewComment] = submission.reviewComment
        statement[SubmissionAttemptsTable.score] = submission.score
        statement[SubmissionAttemptsTable.reviewedAt] = submission.reviewedAt
    }

    private fun replaceSubmissionLinks(submission: SubmissionAttempt) {
        SubmissionLinksTable.deleteWhere { SubmissionLinksTable.submissionId eq submission.id }
        submission.links.forEachIndexed { index, link ->
            SubmissionLinksTable.insert {
                it[submissionId] = submission.id
                it[position] = index
                it[url] = link
            }
        }
    }

    private fun ResultRow.toAssignment(): Assignment {
        val assignmentId = this[AssignmentsTable.id]
        val links = AssignmentLinksTable.selectAll().where {
            AssignmentLinksTable.assignmentId eq assignmentId
        }.orderBy(AssignmentLinksTable.position).map { it[AssignmentLinksTable.url] }
        val targets = AssignmentTargetsTable.selectAll().where {
            AssignmentTargetsTable.assignmentId eq assignmentId
        }.mapTo(linkedSetOf()) { it[AssignmentTargetsTable.studentAccountId] }
        return Assignment(
            assignmentId,
            this[AssignmentsTable.lessonId],
            this[AssignmentsTable.teacherAccountId],
            this[AssignmentsTable.text],
            links,
            this[AssignmentsTable.deadline],
            this[AssignmentsTable.createdAt],
            targets,
        )
    }

    private fun ResultRow.toSubmission(): SubmissionAttempt {
        val submissionId = this[SubmissionAttemptsTable.id]
        val links = SubmissionLinksTable.selectAll().where {
            SubmissionLinksTable.submissionId eq submissionId
        }.orderBy(SubmissionLinksTable.position).map { it[SubmissionLinksTable.url] }
        return SubmissionAttempt(
            submissionId,
            this[SubmissionAttemptsTable.assignmentId],
            this[SubmissionAttemptsTable.studentAccountId],
            this[SubmissionAttemptsTable.attemptNumber],
            this[SubmissionAttemptsTable.text],
            links,
            this[SubmissionAttemptsTable.status],
            this[SubmissionAttemptsTable.createdAt],
            this[SubmissionAttemptsTable.updatedAt],
            this[SubmissionAttemptsTable.submittedAt],
            this[SubmissionAttemptsTable.reviewerAccountId],
            this[SubmissionAttemptsTable.reviewComment],
            this[SubmissionAttemptsTable.score],
            this[SubmissionAttemptsTable.reviewedAt],
        )
    }

    private fun ResultRow.toAssessment() = Assessment(
        this[AssessmentsTable.id], this[AssessmentsTable.lessonId], this[AssessmentsTable.courseId],
        this[AssessmentsTable.studentAccountId], this[AssessmentsTable.teacherAccountId],
        this[AssessmentsTable.version], this[AssessmentsTable.score], this[AssessmentsTable.comment],
        this[AssessmentsTable.createdAt],
    )

    private fun ResultRow.toFeedback() = LessonFeedback(
        this[FeedbackTable.lessonId], this[FeedbackTable.studentAccountId], this[FeedbackTable.mood],
        this[FeedbackTable.difficulty], this[FeedbackTable.comment], this[FeedbackTable.createdAt],
        this[FeedbackTable.updatedAt],
    )
}
