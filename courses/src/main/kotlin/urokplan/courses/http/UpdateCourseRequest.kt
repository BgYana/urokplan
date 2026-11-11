package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class UpdateCourseRequest(
    val title: String,
    val subject: String,
    val callLink: String? = null,
)
