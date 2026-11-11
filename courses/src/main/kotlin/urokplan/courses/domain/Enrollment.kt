package urokplan.courses.domain

import java.time.Instant
import java.util.UUID

data class Enrollment(
    val courseId: UUID,
    val studentAccountId: UUID,
    val priceMinor: Long,
    val status: ParticipationStatus,
    val enrolledAt: Instant,
    val endedAt: Instant? = null,
)
