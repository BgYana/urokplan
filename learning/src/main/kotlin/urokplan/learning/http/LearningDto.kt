package urokplan.learning.http

import kotlinx.serialization.Serializable
import urokplan.learning.application.AssessmentCommand
import urokplan.learning.application.AssessmentSummaryView
import urokplan.learning.application.AssessmentView
import urokplan.learning.application.AssignmentStudentView
import urokplan.learning.application.AssignmentView
import urokplan.learning.application.CreateAssignmentCommand
import urokplan.learning.application.FeedbackCommand
import urokplan.learning.application.FeedbackView
import urokplan.learning.application.ReviewDecision
import urokplan.learning.application.ReviewSubmissionCommand
import urokplan.learning.application.SaveDraftCommand
import urokplan.learning.application.SubmissionView
import java.time.Instant
import java.util.UUID

@Serializable
data class CreateAssignmentRequest(
    val lessonId: String,
    val text: String,
    val links: List<String> = emptyList(),
    val deadline: String? = null,
    val targetStudentPublicIds: Set<String>,
)

@Serializable
data class SaveDraftRequest(val text: String? = null, val links: List<String> = emptyList())

@Serializable
data class ReviewSubmissionRequest(val decision: String, val comment: String, val score: Int)

@Serializable
data class AssessmentRequest(val score: Int, val comment: String? = null)

@Serializable
data class FeedbackRequest(val mood: Int, val difficulty: Int, val comment: String? = null)

@Serializable
data class AssignmentResponse(
    val id: String,
    val lessonId: String,
    val text: String,
    val links: List<String>,
    val deadline: String?,
    val overdue: Boolean,
    val createdAt: String,
    val students: List<AssignmentStudentResponse>,
)

@Serializable
data class AssignmentStudentResponse(
    val studentPublicId: String,
    val studentDisplayName: String,
    val submissions: List<SubmissionResponse>,
)

@Serializable
data class SubmissionResponse(
    val id: String,
    val attemptNumber: Int,
    val text: String?,
    val links: List<String>,
    val status: String,
    val submittedAt: String?,
    val submittedLate: Boolean,
    val reviewComment: String?,
    val score: Int?,
    val reviewedAt: String?,
)

@Serializable
data class AssessmentResponse(
    val id: String,
    val lessonId: String,
    val version: Int,
    val score: Int,
    val comment: String?,
    val createdAt: String,
)

@Serializable
data class AssessmentSummaryResponse(
    val studentPublicId: String,
    val averageScore: Double?,
    val assessments: List<AssessmentResponse>,
)

@Serializable
data class FeedbackResponse(
    val studentPublicId: String,
    val mood: Int,
    val difficulty: Int,
    val comment: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class LearningErrorResponse(val code: String)

internal fun CreateAssignmentRequest.toCommand() = CreateAssignmentCommand(
    UUID.fromString(lessonId), text, links, deadline?.let(Instant::parse), targetStudentPublicIds,
)

internal fun SaveDraftRequest.toCommand() = SaveDraftCommand(text, links)
internal fun ReviewSubmissionRequest.toCommand() = ReviewSubmissionCommand(
    ReviewDecision.valueOf(decision), comment, score,
)
internal fun AssessmentRequest.toCommand() = AssessmentCommand(score, comment)
internal fun FeedbackRequest.toCommand() = FeedbackCommand(mood, difficulty, comment)

internal fun AssignmentView.toResponse() = AssignmentResponse(
    id.toString(), lessonId.toString(), text, links, deadline?.toString(), overdue, createdAt.toString(),
    students.map(AssignmentStudentView::toResponse),
)

internal fun AssignmentStudentView.toResponse() = AssignmentStudentResponse(
    studentPublicId, studentDisplayName, submissions.map(SubmissionView::toResponse),
)

internal fun SubmissionView.toResponse() = SubmissionResponse(
    id.toString(), attemptNumber, text, links, status.name, submittedAt?.toString(), submittedLate,
    reviewComment, score, reviewedAt?.toString(),
)

internal fun AssessmentSummaryView.toResponse() = AssessmentSummaryResponse(
    studentPublicId, averageScore, assessments.map(AssessmentView::toResponse),
)

private fun AssessmentView.toResponse() = AssessmentResponse(
    id.toString(), lessonId.toString(), version, score, comment, createdAt.toString(),
)

internal fun FeedbackView.toResponse() = FeedbackResponse(
    studentPublicId, mood, difficulty, comment, createdAt.toString(), updatedAt.toString(),
)
