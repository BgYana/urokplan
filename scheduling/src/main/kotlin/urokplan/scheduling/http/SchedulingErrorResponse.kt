package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class SchedulingErrorResponse(val code: String)
