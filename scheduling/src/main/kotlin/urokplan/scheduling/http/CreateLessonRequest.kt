package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class CreateLessonRequest(
    val courseId: String,
    val groupId: String? = null,
    val startsAt: String,
    val durationMinutes: Int,
    val participants: List<ParticipantPriceRequest> = emptyList(),
    val callLink: String? = null,
)
