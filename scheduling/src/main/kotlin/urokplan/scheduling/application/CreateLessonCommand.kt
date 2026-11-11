package urokplan.scheduling.application

import java.time.Instant
import java.util.UUID

data class CreateLessonCommand(
    val courseId: UUID,
    val groupId: UUID?,
    val startsAt: Instant,
    val durationMinutes: Int,
    val participants: List<ParticipantPriceCommand>,
    val callLink: String?,
)
