package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class CreateCourseRequest(
    val title: String,
    val subject: String,
    val format: CourseFormatDto,
    val callLink: String? = null,
)
