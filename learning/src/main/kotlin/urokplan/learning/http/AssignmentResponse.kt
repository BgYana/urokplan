package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class AssignmentResponse(
    val id: String,
    val lessonId: String,
    val text: String,
    val links: List<String>,
    val deadline: String?,
    val overdue: Boolean,
    val createdAt: String,
    val students: List<AssignmentStudentResponse>,
)
