package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class LedgerEntryResponse(
    val id: String,
    val type: String,
    val unitsDelta: Int,
    val paymentId: String?,
    val lessonId: String?,
    val createdAt: String,
    val reason: String?,
)
