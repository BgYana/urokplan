package urokplan.learning.application

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

enum class ReviewDecision {
    REVISION_REQUESTED,
    ACCEPTED,
}

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
