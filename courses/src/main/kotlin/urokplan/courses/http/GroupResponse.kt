package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class GroupResponse(
    val id: String,
    val courseId: String,
    val name: String,
    val callLink: String?,
    val createdAt: String,
    val updatedAt: String,
)
