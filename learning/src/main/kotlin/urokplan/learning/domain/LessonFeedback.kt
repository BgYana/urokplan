package urokplan.learning.domain

import java.time.Instant
import java.util.UUID

data class LessonFeedback(
    val lessonId: UUID,
    val studentAccountId: UUID,
    val mood: Int,
    val difficulty: Int,
    val comment: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
