package urokplan.learning.application

import java.time.Instant

data class FeedbackView(
    val studentPublicId: String,
    val mood: Int,
    val difficulty: Int,
    val comment: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
