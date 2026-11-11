package urokplan.learning.application

import java.time.Instant
import java.util.UUID

data class AssessmentView(
    val id: UUID,
    val lessonId: UUID,
    val version: Int,
    val score: Int,
    val comment: String?,
    val createdAt: Instant,
)
