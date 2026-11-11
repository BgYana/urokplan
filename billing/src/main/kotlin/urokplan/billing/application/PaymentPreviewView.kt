package urokplan.billing.application

import java.util.UUID

data class PaymentPreviewView(
    val unitsToBuy: Int,
    val unitPriceMinor: Long,
    val amountMinor: Long,
    val debtLessonIds: List<UUID>,
    val futureLessonIds: List<UUID>,
    val remainingUnits: Int,
)
