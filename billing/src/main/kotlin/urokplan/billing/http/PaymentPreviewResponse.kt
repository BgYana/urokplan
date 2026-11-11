package urokplan.billing.http

import kotlinx.serialization.Serializable

@Serializable
data class PaymentPreviewResponse(
    val unitsToBuy: Int,
    val unitPriceMinor: Long,
    val amountMinor: Long,
    val debtLessonIds: List<String>,
    val futureLessonIds: List<String>,
    val remainingUnits: Int,
)
