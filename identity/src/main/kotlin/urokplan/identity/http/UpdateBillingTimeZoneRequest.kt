package urokplan.identity.http

import kotlinx.serialization.Serializable

@Serializable
data class UpdateBillingTimeZoneRequest(val billingTimeZone: String)
