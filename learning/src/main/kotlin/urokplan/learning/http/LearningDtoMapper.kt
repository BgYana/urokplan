package urokplan.learning.http

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
