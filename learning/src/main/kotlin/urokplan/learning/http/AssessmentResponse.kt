package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class AssessmentResponse(
    val id: String,
    val lessonId: String,
    val version: Int,
    val score: Int,
    val comment: String?,
    val createdAt: String,
)
