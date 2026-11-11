package urokplan.billing.domain

import java.time.Instant
import java.util.UUID

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
