package urokplan.billing.application

import java.util.UUID

data class PaymentSummaryView(
    val courseId: UUID,
    val studentPublicId: String,
    val availableUnits: Int,
    val charges: List<ChargeView>,
    val payments: List<PaymentView>,
    val ledger: List<LedgerEntryView>,
    val futureCoveredLessonIds: List<UUID>,
)
