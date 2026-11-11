package urokplan.billing.domain

import java.time.Instant
import java.util.UUID

data class LedgerEntry(
    val id: UUID,
    val courseId: UUID,
    val studentAccountId: UUID,
    val type: LedgerEntryType,
    val unitsDelta: Int,
    val paymentId: UUID?,
    val lessonId: UUID?,
    val createdAt: Instant,
    val actorAccountId: UUID?,
    val reason: String?,
)
