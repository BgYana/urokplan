package urokplan.learning.application

import urokplan.courses.application.CoursesApi
import urokplan.database.TransactionRunner
import urokplan.identity.application.AccountReference
import urokplan.identity.application.IdentityApi
import urokplan.learning.domain.Assessment
import urokplan.learning.domain.Assignment
import urokplan.learning.domain.LessonFeedback
import urokplan.learning.domain.SubmissionAttempt
import urokplan.learning.domain.SubmissionStatus
import urokplan.relationships.application.RelationshipsApi
import urokplan.scheduling.application.LessonReference
import urokplan.scheduling.application.SchedulingApi
import urokplan.scheduling.domain.LessonStatus
import java.net.URI
import java.time.Clock
import java.util.UUID

internal class LearningService(
    private val transactions: TransactionRunner,
    private val repository: LearningRepository,
    private val identity: IdentityApi,
    private val relationships: RelationshipsApi,
    private val courses: CoursesApi,
    private val scheduling: SchedulingApi,
    private val clock: Clock,
) : LearningUseCases {
    override fun createAssignment(actorId: UUID, command: CreateAssignmentCommand): AssignmentView {
        val lesson = requireLesson(command.lessonId)
        if (lesson.teacherId != actorId) forbidden("lesson_teacher_required")
        if (lesson.status == LessonStatus.CANCELLED) conflict("lesson_cancelled")
        val text = command.text.trim()
        if (text.isEmpty()) badRequest("assignment_text_required")
        val links = validateLinks(command.links)
        val targets = command.targetStudentPublicIds.mapTo(linkedSetOf()) { publicId ->
            requireAccount(publicId).id
        }
        if (targets.isEmpty()) badRequest("assignment_targets_required")
        val participantIds = lesson.participants.mapTo(hashSetOf()) { it.studentId }
        if (!participantIds.containsAll(targets)) badRequest("assignment_target_not_participant")
        val assignment = Assignment(
            UUID.randomUUID(), lesson.id, actorId, text, links, command.deadline,
            clock.instant(), targets,
        )
        val created = transactions.inTransaction { repository.createAssignment(assignment) }
        return created.toView(actorId)
    }

    override fun listAssignments(actorId: UUID, lessonId: UUID?): List<AssignmentView> =
        transactions.inTransaction { repository.listAssignments(lessonId) }
            .filter { canReadAssignment(actorId, it) }
            .map { it.toView(actorId) }

    override fun getAssignment(actorId: UUID, assignmentId: UUID): AssignmentView {
        val assignment = transactions.inTransaction { repository.findAssignment(assignmentId) }
            ?: notFound("assignment_not_found")
        if (!canReadAssignment(actorId, assignment)) notFound("assignment_not_found")
        return assignment.toView(actorId)
    }

    override fun saveDraft(actorId: UUID, assignmentId: UUID, command: SaveDraftCommand): SubmissionView {
        val assignment = requireAssignment(assignmentId)
        if (actorId !in assignment.targetStudentIds) forbidden("assignment_student_required")
        val text = command.text?.trim()?.takeIf(String::isNotEmpty)
        val links = validateLinks(command.links)
        val now = clock.instant()
        val saved = transactions.inTransaction {
            val latest = repository.findLatestSubmission(assignmentId, actorId)
            when (latest?.status) {
                null -> repository.createSubmission(
                    SubmissionAttempt(UUID.randomUUID(), assignmentId, actorId, 1, text, links,
                        SubmissionStatus.DRAFT, now, now),
                )
                SubmissionStatus.DRAFT -> repository.updateSubmission(
                    latest.copy(text = text, links = links, updatedAt = now),
                )
                SubmissionStatus.REVISION_REQUESTED -> repository.createSubmission(
                    SubmissionAttempt(UUID.randomUUID(), assignmentId, actorId, latest.attemptNumber + 1,
                        text, links, SubmissionStatus.DRAFT, now, now),
                )
                SubmissionStatus.SUBMITTED, SubmissionStatus.ACCEPTED -> conflict("submission_not_editable")
            }
        }
        return saved.toView(assignment)
    }

    override fun submit(actorId: UUID, assignmentId: UUID): SubmissionView {
        val assignment = requireAssignment(assignmentId)
        if (actorId !in assignment.targetStudentIds) forbidden("assignment_student_required")
        val now = clock.instant()
        val submitted = transactions.inTransaction {
            val draft = repository.findLatestSubmission(assignmentId, actorId)
                ?: conflict("submission_draft_required")
            if (draft.status != SubmissionStatus.DRAFT) conflict("submission_draft_required")
            if (draft.text.isNullOrBlank() && draft.links.isEmpty()) badRequest("submission_content_required")
            repository.updateSubmission(
                draft.copy(status = SubmissionStatus.SUBMITTED, updatedAt = now, submittedAt = now),
            )
        }
        return submitted.toView(assignment)
    }

    override fun reviewSubmission(
        actorId: UUID,
        submissionId: UUID,
        command: ReviewSubmissionCommand,
    ): SubmissionView {
        if (command.score !in 0..10) badRequest("invalid_score")
        val comment = command.comment.trim()
        if (comment.isEmpty()) badRequest("review_comment_required")
        val now = clock.instant()
        val pair = transactions.inTransaction {
            val submission = repository.findSubmission(submissionId) ?: notFound("submission_not_found")
            val assignment = repository.findAssignment(submission.assignmentId) ?: notFound("assignment_not_found")
            if (assignment.teacherAccountId != actorId) forbidden("lesson_teacher_required")
            if (submission.status != SubmissionStatus.SUBMITTED) conflict("submission_not_reviewable")
            val status = when (command.decision) {
                ReviewDecision.REVISION_REQUESTED -> SubmissionStatus.REVISION_REQUESTED
                ReviewDecision.ACCEPTED -> SubmissionStatus.ACCEPTED
            }
            val reviewed = repository.updateSubmission(
                submission.copy(
                    status = status,
                    reviewerAccountId = actorId,
                    reviewComment = comment,
                    score = command.score,
                    reviewedAt = now,
                    updatedAt = now,
                ),
            )
            assignment to reviewed
        }
        return pair.second.toView(pair.first)
    }

    override fun putAssessment(
        actorId: UUID,
        lessonId: UUID,
        studentPublicId: String,
        command: AssessmentCommand,
    ): AssessmentSummaryView {
        if (command.score !in 0..10) badRequest("invalid_score")
        val lesson = requireLesson(lessonId)
        if (lesson.teacherId != actorId) forbidden("lesson_teacher_required")
        requireFinishedLesson(lesson)
        val student = requireAccount(studentPublicId)
        requireParticipant(lesson, student.id)
        transactions.inTransaction {
            val history = repository.listAssessments(student.id, lesson.courseId, lesson.id)
            repository.createAssessment(
                Assessment(
                    UUID.randomUUID(), lesson.id, lesson.courseId, student.id, actorId,
                    (history.maxOfOrNull { it.version } ?: 0) + 1,
                    command.score, command.comment?.trim()?.takeIf(String::isNotEmpty), clock.instant(),
                ),
            )
        }
        return assessmentSummary(student, lesson.courseId, lesson.id)
    }

    override fun getAssessment(actorId: UUID, lessonId: UUID, studentPublicId: String): AssessmentSummaryView {
        val lesson = requireLesson(lessonId)
        val student = requireAccount(studentPublicId)
        requireParticipant(lesson, student.id)
        requireStudentReadAccess(actorId, lesson.teacherId, student.id)
        return assessmentSummary(student, lesson.courseId, lesson.id)
    }

    override fun listAssessments(actorId: UUID, courseId: UUID, studentPublicId: String): AssessmentSummaryView {
        val course = courses.findCourse(courseId) ?: notFound("course_not_found")
        val student = requireAccount(studentPublicId)
        if (courses.findEnrollment(courseId, student.id) == null) notFound("enrollment_not_found")
        requireStudentReadAccess(actorId, course.teacherId, student.id)
        return assessmentSummary(student, courseId, null)
    }

    override fun putFeedback(actorId: UUID, lessonId: UUID, command: FeedbackCommand): FeedbackView {
        if (command.mood !in 1..5) badRequest("invalid_mood")
        if (command.difficulty !in 1..5) badRequest("invalid_difficulty")
        val lesson = requireLesson(lessonId)
        requireParticipant(lesson, actorId)
        requireFinishedLesson(lesson)
        val now = clock.instant()
        val existing = transactions.inTransaction {
            repository.listFeedback(lessonId, setOf(actorId)).singleOrNull()
        }
        val feedback = LessonFeedback(
            lessonId, actorId, command.mood, command.difficulty,
            command.comment?.trim()?.takeIf(String::isNotEmpty), existing?.createdAt ?: now, now,
        )
        val saved = transactions.inTransaction { repository.upsertFeedback(feedback) }
        return saved.toView(requireAccounts(setOf(actorId)))
    }

    override fun getFeedback(actorId: UUID, lessonId: UUID): List<FeedbackView> {
        val lesson = requireLesson(lessonId)
        val visibleStudentIds = when {
            actorId == lesson.teacherId -> lesson.participants.mapTo(linkedSetOf()) { it.studentId }
            lesson.participants.any { it.studentId == actorId } -> setOf(actorId)
            else -> notFound("feedback_not_found")
        }
        val feedback = transactions.inTransaction { repository.listFeedback(lessonId, visibleStudentIds) }
        val accounts = requireAccounts(visibleStudentIds)
        return feedback.map { it.toView(accounts) }
    }

    private fun Assignment.toView(actorId: UUID): AssignmentView {
        val visibleIds = when {
            actorId == teacherAccountId -> targetStudentIds
            actorId in targetStudentIds -> setOf(actorId)
            else -> targetStudentIds.intersect(relationships.findActiveGuardianStudentIds(actorId))
        }
        val accounts = requireAccounts(visibleIds)
        val submissions = transactions.inTransaction { repository.listSubmissions(id, visibleIds) }
            .groupBy { it.studentAccountId }
        return AssignmentView(
            id, lessonId, text, links, deadline,
            deadline?.let { clock.instant() > it } ?: false,
            createdAt,
            visibleIds.map { studentId ->
                val account = accounts.getValue(studentId)
                AssignmentStudentView(
                    account.publicId,
                    account.displayName,
                    submissions[studentId].orEmpty().map { it.toView(this) },
                )
            },
        )
    }

    private fun SubmissionAttempt.toView(assignment: Assignment) = SubmissionView(
        id, attemptNumber, text, links, status, submittedAt,
        submittedAt?.let { submitted -> assignment.deadline?.let { submitted > it } ?: false } ?: false,
        reviewComment, score, reviewedAt,
    )

    private fun assessmentSummary(
        student: AccountReference,
        courseId: UUID,
        lessonId: UUID?,
    ): AssessmentSummaryView {
        val assessments = transactions.inTransaction {
            repository.listAssessments(student.id, courseId, lessonId)
        }
        val latest = assessments.groupBy { it.lessonId }.values.map { versions -> versions.maxBy { it.version } }
        return AssessmentSummaryView(
            student.publicId,
            latest.takeIf(List<*>::isNotEmpty)?.map { it.score }?.average(),
            assessments.map { AssessmentView(it.id, it.lessonId, it.version, it.score, it.comment, it.createdAt) },
        )
    }

    private fun LessonFeedback.toView(accounts: Map<UUID, AccountReference>) = FeedbackView(
        accounts.getValue(studentAccountId).publicId, mood, difficulty, comment, createdAt, updatedAt,
    )

    private fun canReadAssignment(actorId: UUID, assignment: Assignment): Boolean =
        actorId == assignment.teacherAccountId || actorId in assignment.targetStudentIds ||
            assignment.targetStudentIds.any { it in relationships.findActiveGuardianStudentIds(actorId) }

    private fun requireStudentReadAccess(actorId: UUID, teacherId: UUID, studentId: UUID) {
        if (actorId != teacherId && actorId != studentId &&
            studentId !in relationships.findActiveGuardianStudentIds(actorId)
        ) forbidden("learning_access_denied")
    }

    private fun requireFinishedLesson(lesson: LessonReference) {
        if (lesson.status == LessonStatus.CANCELLED) conflict("lesson_cancelled")
        if (lesson.endsAt > clock.instant()) conflict("lesson_not_finished")
    }

    private fun requireParticipant(lesson: LessonReference, studentId: UUID) {
        if (lesson.participants.none { it.studentId == studentId }) notFound("lesson_participant_not_found")
    }

    private fun requireLesson(id: UUID) = scheduling.findLesson(id) ?: notFound("lesson_not_found")
    private fun requireAssignment(id: UUID) = transactions.inTransaction {
        repository.findAssignment(id)
    } ?: notFound("assignment_not_found")
    private fun requireAccount(publicId: String) =
        identity.findAccountByPublicId(publicId) ?: notFound("account_not_found")
    private fun requireAccounts(ids: Set<UUID>): Map<UUID, AccountReference> = identity.findAccountsByIds(ids).also {
        if (it.size != ids.size) notFound("account_not_found")
    }

    private fun validateLinks(values: List<String>): List<String> {
        if (values.size > 20) badRequest("too_many_links")
        return values.map { value ->
            val trimmed = value.trim()
            val valid = runCatching { URI(trimmed) }.getOrNull()?.let { it.scheme in setOf("http", "https") } == true
            if (!valid || trimmed.length > 2_000) badRequest("invalid_link")
            trimmed
        }.distinct()
    }

    private fun badRequest(code: String): Nothing = throw LearningException(code, LearningErrorKind.BAD_REQUEST)
    private fun forbidden(code: String): Nothing = throw LearningException(code, LearningErrorKind.FORBIDDEN)
    private fun notFound(code: String): Nothing = throw LearningException(code, LearningErrorKind.NOT_FOUND)
    private fun conflict(code: String): Nothing = throw LearningException(code, LearningErrorKind.CONFLICT)
}
