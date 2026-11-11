package urokplan.courses.application

import java.time.Instant
import java.util.UUID

data class GroupView(
    val id: UUID,
    val courseId: UUID,
    val name: String,
    val callLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
