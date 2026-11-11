package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class RescheduleLessonRequest(
    val startsAt: String,
    val durationMinutes: Int,
    val scope: String,
)
