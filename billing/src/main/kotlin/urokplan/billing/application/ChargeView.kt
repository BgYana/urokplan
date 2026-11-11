package urokplan.billing.application

import urokplan.billing.domain.ChargeStatus
import java.time.Instant
import java.util.UUID

data class ChargeView(
    val lessonId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: ChargeStatus,
    val overdue: Boolean,
)
