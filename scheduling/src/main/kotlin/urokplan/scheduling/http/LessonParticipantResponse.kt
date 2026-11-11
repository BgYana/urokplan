package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class LessonParticipantResponse(
    val student: SchedulingAccountResponse,
    val priceMinor: Long,
    val attendance: String,
)
