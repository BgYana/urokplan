package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class SeriesResponse(
    val id: String,
    val courseId: String,
    val localTime: String,
    val zoneId: String,
    val daysOfWeek: List<String>,
    val startDate: String,
    val endDate: String,
    val durationMinutes: Int,
    val status: String,
    val lessonCount: Int,
)
