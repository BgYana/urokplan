package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class AssignmentStudentResponse(
    val studentPublicId: String,
    val studentDisplayName: String,
    val submissions: List<SubmissionResponse>,
)
