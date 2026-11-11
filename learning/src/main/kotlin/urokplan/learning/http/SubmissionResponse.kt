package urokplan.learning.http

import kotlinx.serialization.Serializable

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
