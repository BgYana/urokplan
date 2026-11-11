package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class CourseAccountResponse(
    val publicId: String,
    val displayName: String,
    val roles: List<String>,
)
