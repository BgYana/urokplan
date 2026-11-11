package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class ChargeResponse(
    val lessonId: String,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val overdue: Boolean,
)
