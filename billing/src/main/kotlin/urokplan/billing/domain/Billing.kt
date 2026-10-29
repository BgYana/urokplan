package urokplan.billing.domain

import java.time.Instant
import java.util.UUID

enum class PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
}

enum class LedgerEntryType {
    TOP_UP,
    CONSUMPTION,
    REFUND,
}

enum class ChargeStatus {
    OPEN,
    COVERED,
    WAIVED,
}

data class Balance(
    val courseId: UUID,
    val studentAccountId: UUID,
    val availableUnits: Int,
    val updatedAt: Instant,
)

data class Payment(
    val id: UUID,
    val courseId: UUID,
    val studentAccountId: UUID,
    val payerAccountId: UUID,
    val units: Int,
    val unitPriceMinor: Long,
    val amountMinor: Long,
    val status: PaymentStatus,
    val providerName: String,
    val providerPaymentId: String,
    val confirmationUrl: String?,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
    val failureCode: String? = null,
)

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
