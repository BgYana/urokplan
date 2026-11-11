package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class BillingErrorResponse(val code: String)
