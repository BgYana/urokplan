package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class AssessmentSummaryResponse(
    val studentPublicId: String,
    val averageScore: Double?,
    val assessments: List<AssessmentResponse>,
)
