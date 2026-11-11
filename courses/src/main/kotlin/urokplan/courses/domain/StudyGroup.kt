package urokplan.courses.domain

import java.time.Instant
import java.util.UUID

data class StudyGroup(
    val id: UUID,
    val courseId: UUID,
    val name: String,
    val callLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
