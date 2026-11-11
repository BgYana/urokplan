package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class PaymentSummaryResponse(
    val courseId: String,
    val studentPublicId: String,
    val availableUnits: Int,
    val charges: List<ChargeResponse>,
    val payments: List<PaymentResponse>,
    val ledger: List<LedgerEntryResponse>,
    val futureCoveredLessonIds: List<String>,
)
