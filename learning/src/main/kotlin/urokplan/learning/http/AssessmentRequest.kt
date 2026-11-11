package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class AssessmentRequest(val score: Int, val comment: String? = null)
