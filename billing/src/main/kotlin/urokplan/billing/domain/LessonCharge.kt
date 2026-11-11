package urokplan.billing.domain

import java.time.Instant
import java.util.UUID

data class LessonCharge(
    val lessonId: UUID,
    val studentAccountId: UUID,
    val courseId: UUID,
    val lessonStartsAt: Instant,
    val lessonEndsAt: Instant,
    val status: ChargeStatus,
    val createdAt: Instant,
    val coveredAt: Instant? = null,
    val waivedAt: Instant? = null,
)
