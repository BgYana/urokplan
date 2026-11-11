package urokplan.learning.domain

import java.time.Instant
import java.util.UUID

data class Assessment(
    val id: UUID,
    val lessonId: UUID,
    val courseId: UUID,
    val studentAccountId: UUID,
    val teacherAccountId: UUID,
    val version: Int,
    val score: Int,
    val comment: String?,
    val createdAt: Instant,
)
