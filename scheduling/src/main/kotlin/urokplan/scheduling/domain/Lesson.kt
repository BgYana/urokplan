package urokplan.scheduling.domain

import java.time.Instant
import java.util.UUID

data class Lesson(
    val id: UUID,
    val seriesId: UUID?,
    val courseId: UUID,
    val teacherAccountId: UUID,
    val groupId: UUID?,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: LessonStatus,
    val callLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
