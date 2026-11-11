package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class UpdateAttendanceRequest(
    val status: String,
    val reason: String? = null,
)
