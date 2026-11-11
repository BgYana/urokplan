package urokplan.scheduling.application

import urokplan.scheduling.domain.LessonStatus
import java.time.Instant
import java.util.UUID

data class LessonReference(
    val id: UUID,
    val courseId: UUID,
    val teacherId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: LessonStatus,
    val participants: List<ParticipantReference>,
)
