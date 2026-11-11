package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class ReviewSubmissionRequest(val decision: String, val comment: String, val score: Int)
