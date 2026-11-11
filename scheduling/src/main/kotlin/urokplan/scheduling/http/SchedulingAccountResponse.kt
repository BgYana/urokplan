package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class SchedulingAccountResponse(
    val publicId: String,
    val displayName: String,
    val roles: List<String>,
)
