package urokplan.learning.application

import urokplan.learning.domain.SubmissionStatus
import java.time.Instant
import java.util.UUID

interface LearningUseCases {
    fun createAssignment(actorId: UUID, command: CreateAssignmentCommand): AssignmentView
    fun listAssignments(actorId: UUID, lessonId: UUID?): List<AssignmentView>
    fun getAssignment(actorId: UUID, assignmentId: UUID): AssignmentView
    fun saveDraft(actorId: UUID, assignmentId: UUID, command: SaveDraftCommand): SubmissionView
    fun submit(actorId: UUID, assignmentId: UUID): SubmissionView
    fun reviewSubmission(actorId: UUID, submissionId: UUID, command: ReviewSubmissionCommand): SubmissionView

    fun putAssessment(actorId: UUID, lessonId: UUID, studentPublicId: String, command: AssessmentCommand): AssessmentSummaryView
    fun getAssessment(actorId: UUID, lessonId: UUID, studentPublicId: String): AssessmentSummaryView
    fun listAssessments(actorId: UUID, courseId: UUID, studentPublicId: String): AssessmentSummaryView

    fun putFeedback(actorId: UUID, lessonId: UUID, command: FeedbackCommand): FeedbackView
    fun getFeedback(actorId: UUID, lessonId: UUID): List<FeedbackView>
}

data class CreateAssignmentCommand(
    val lessonId: UUID,
    val text: String,
    val links: List<String>,
    val deadline: Instant?,
    val targetStudentPublicIds: Set<String>,
)

data class SaveDraftCommand(val text: String?, val links: List<String>)

enum class ReviewDecision {
    REVISION_REQUESTED,
    ACCEPTED,
}

data class ReviewSubmissionCommand(
    val decision: ReviewDecision,
    val comment: String,
    val score: Int,
)

data class AssessmentCommand(val score: Int, val comment: String?)
data class FeedbackCommand(val mood: Int, val difficulty: Int, val comment: String?)

data class AssignmentView(
    val id: UUID,
    val lessonId: UUID,
    val text: String,
    val links: List<String>,
    val deadline: Instant?,
    val overdue: Boolean,
    val createdAt: Instant,
    val students: List<AssignmentStudentView>,
)

data class AssignmentStudentView(
    val studentPublicId: String,
    val studentDisplayName: String,
    val submissions: List<SubmissionView>,
)

data class SubmissionView(
    val id: UUID,
    val attemptNumber: Int,
    val text: String?,
    val links: List<String>,
    val status: SubmissionStatus,
    val submittedAt: Instant?,
    val submittedLate: Boolean,
    val reviewComment: String?,
    val score: Int?,
    val reviewedAt: Instant?,
)

data class AssessmentView(
    val id: UUID,
    val lessonId: UUID,
    val version: Int,
    val score: Int,
    val comment: String?,
    val createdAt: Instant,
)

data class AssessmentSummaryView(
    val studentPublicId: String,
    val averageScore: Double?,
    val assessments: List<AssessmentView>,
)

data class FeedbackView(
    val studentPublicId: String,
    val mood: Int,
    val difficulty: Int,
    val comment: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class LearningErrorKind {
    BAD_REQUEST,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
}

class LearningException(
    val errorCode: String,
    val kind: LearningErrorKind,
) : RuntimeException(errorCode)
