package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePriceRequest(val priceMinor: Long)
