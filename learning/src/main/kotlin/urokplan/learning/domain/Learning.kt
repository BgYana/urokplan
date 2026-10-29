package urokplan.learning.domain

import java.time.Instant
import java.util.UUID

enum class SubmissionStatus {
    DRAFT,
    SUBMITTED,
    REVISION_REQUESTED,
    ACCEPTED,
}

data class Assignment(
    val id: UUID,
    val lessonId: UUID,
    val teacherAccountId: UUID,
    val text: String,
    val links: List<String>,
    val deadline: Instant?,
    val createdAt: Instant,
    val targetStudentIds: Set<UUID>,
)

data class SubmissionAttempt(
    val id: UUID,
    val assignmentId: UUID,
    val studentAccountId: UUID,
    val attemptNumber: Int,
    val text: String?,
    val links: List<String>,
    val status: SubmissionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val submittedAt: Instant? = null,
    val reviewerAccountId: UUID? = null,
    val reviewComment: String? = null,
    val score: Int? = null,
    val reviewedAt: Instant? = null,
)

data class Assessment(
    val id: UUID,
    val lessonId: UUID,
    val courseId: UUID,
    val studentAccountId: UUID,
    val teacherAccountId: UUID,
    val version: Int,
    val score: Int,
    val comment: String?,
    val createdAt: Instant,
)

data class LessonFeedback(
    val lessonId: UUID,
    val studentAccountId: UUID,
    val mood: Int,
    val difficulty: Int,
    val comment: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
