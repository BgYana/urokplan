package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class CreateSeriesRequest(
    val courseId: String,
    val groupId: String? = null,
    val localTime: String,
    val zoneId: String,
    val daysOfWeek: Set<String>,
    val startDate: String,
    val endDate: String,
    val durationMinutes: Int,
    val participants: List<ParticipantPriceRequest> = emptyList(),
    val callLink: String? = null,
)
