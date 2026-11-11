package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackResponse(
    val studentPublicId: String,
    val mood: Int,
    val difficulty: Int,
    val comment: String?,
    val createdAt: String,
    val updatedAt: String,
)
