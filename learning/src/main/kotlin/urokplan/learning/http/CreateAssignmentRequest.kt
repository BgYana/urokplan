package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class CreateAssignmentRequest(
    val lessonId: String,
    val text: String,
    val links: List<String> = emptyList(),
    val deadline: String? = null,
    val targetStudentPublicIds: Set<String>,
)
