package urokplan.learning.application

import urokplan.learning.domain.SubmissionStatus
import java.time.Instant
import java.util.UUID

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
