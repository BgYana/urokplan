package urokplan.learning.domain

import java.time.Instant
import java.util.UUID

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
