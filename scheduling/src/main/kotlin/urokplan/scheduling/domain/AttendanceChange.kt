package urokplan.scheduling.domain

import java.time.Instant
import java.util.UUID

data class AttendanceChange(
    val id: UUID,
    val lessonId: UUID,
    val studentAccountId: UUID,
    val previousStatus: AttendanceStatus,
    val newStatus: AttendanceStatus,
    val changedBy: UUID,
    val changedAt: Instant,
    val reason: String?,
)
