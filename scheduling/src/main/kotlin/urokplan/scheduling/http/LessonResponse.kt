package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class LessonResponse(
    val id: String,
    val seriesId: String?,
    val courseId: String,
    val courseTitle: String,
    val groupId: String?,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val callLink: String?,
    val participants: List<LessonParticipantResponse>,
)
