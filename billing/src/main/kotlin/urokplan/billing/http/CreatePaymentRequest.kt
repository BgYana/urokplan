package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class CreatePaymentRequest(
    val courseId: String,
    val studentPublicId: String,
    val units: Int,
)
