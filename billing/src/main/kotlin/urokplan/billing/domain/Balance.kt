package urokplan.billing.domain

import java.time.Instant
import java.util.UUID

data class Balance(
    val courseId: UUID,
    val studentAccountId: UUID,
    val availableUnits: Int,
    val updatedAt: Instant,
)
