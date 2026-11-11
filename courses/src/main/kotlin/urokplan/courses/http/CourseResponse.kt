package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class CourseResponse(
    val id: String,
    val teacher: CourseAccountResponse,
    val title: String,
    val subject: String,
    val format: String,
    val callLink: String?,
    val createdAt: String,
    val updatedAt: String,
)
