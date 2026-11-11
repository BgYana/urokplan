package urokplan.billing.application

import urokplan.billing.domain.LedgerEntryType
import java.time.Instant
import java.util.UUID

data class LedgerEntryView(
    val id: UUID,
    val type: LedgerEntryType,
    val unitsDelta: Int,
    val paymentId: UUID?,
    val lessonId: UUID?,
    val createdAt: Instant,
    val reason: String?,
)
