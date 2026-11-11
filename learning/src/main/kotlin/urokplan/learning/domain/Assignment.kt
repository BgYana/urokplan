package urokplan.learning.domain

import java.time.Instant
import java.util.UUID

data class Assignment(
    val id: UUID,
    val lessonId: UUID,
    val teacherAccountId: UUID,
    val text: String,
    val links: List<String>,
    val deadline: Instant?,
    val createdAt: Instant,
    val targetStudentIds: Set<UUID>,
)
