package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class ResolvePaymentRequest(val outcome: String)
