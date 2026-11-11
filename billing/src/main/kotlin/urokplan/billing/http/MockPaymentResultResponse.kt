package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class MockPaymentResultResponse(val status: String)
