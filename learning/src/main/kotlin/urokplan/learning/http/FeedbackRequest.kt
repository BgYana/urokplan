package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackRequest(val mood: Int, val difficulty: Int, val comment: String? = null)
