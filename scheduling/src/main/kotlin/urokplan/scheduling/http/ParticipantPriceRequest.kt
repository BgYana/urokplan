package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantPriceRequest(
    val studentPublicId: String,
    val priceMinor: Long? = null,
)
