package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class PaymentResponse(
    val id: String,
    val courseId: String,
    val studentPublicId: String,
    val payerPublicId: String,
    val units: Int,
    val unitPriceMinor: Long,
    val amountMinor: Long,
    val status: String,
    val providerName: String,
    val providerPaymentId: String,
    val confirmationUrl: String?,
    val createdAt: String,
    val resolvedAt: String?,
    val failureCode: String?,
)
