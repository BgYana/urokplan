package urokplan.billing.application

import urokplan.billing.domain.PaymentStatus
import java.time.Instant
import java.util.UUID

data class PaymentView(
    val id: UUID,
    val courseId: UUID,
    val studentPublicId: String,
    val payerPublicId: String,
    val units: Int,
    val unitPriceMinor: Long,
    val amountMinor: Long,
    val status: PaymentStatus,
    val providerName: String,
    val providerPaymentId: String,
    val confirmationUrl: String?,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val failureCode: String?,
)
