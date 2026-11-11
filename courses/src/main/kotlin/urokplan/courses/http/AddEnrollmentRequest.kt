package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class AddEnrollmentRequest(
    val studentPublicId: String,
    val priceMinor: Long,
)
