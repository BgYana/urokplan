package urokplan.scheduling.application

import urokplan.scheduling.domain.LessonStatus
import java.time.Instant
import java.util.UUID

data class LessonView(
    val id: UUID,
    val seriesId: UUID?,
    val courseId: UUID,
    val courseTitle: String,
    val groupId: UUID?,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: LessonStatus,
    val callLink: String?,
    val participants: List<LessonParticipantView>,
)
