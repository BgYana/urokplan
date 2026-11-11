package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupRequest(
    val courseId: String,
    val name: String,
    val callLink: String? = null,
)
