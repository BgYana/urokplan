package urokplan.scheduling.domain

import java.time.Instant
import java.util.UUID

data class LessonParticipant(
    val lessonId: UUID,
    val studentAccountId: UUID,
    val priceMinor: Long,
    val attendance: AttendanceStatus,
    val attendanceUpdatedAt: Instant? = null,
    val attendanceUpdatedBy: UUID? = null,
)
