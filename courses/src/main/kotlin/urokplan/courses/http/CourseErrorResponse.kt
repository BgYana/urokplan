package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class CourseErrorResponse(val code: String)
