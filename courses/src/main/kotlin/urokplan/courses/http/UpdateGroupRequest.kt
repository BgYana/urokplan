package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class UpdateGroupRequest(
    val name: String,
    val callLink: String? = null,
)
