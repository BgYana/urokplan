package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class PaymentPreviewRequest(val units: Int)
