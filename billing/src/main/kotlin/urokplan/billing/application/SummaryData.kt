package urokplan.billing.application

import urokplan.billing.domain.LedgerEntry
import urokplan.billing.domain.LessonCharge
import urokplan.billing.domain.Payment

internal data class SummaryData(
    val charges: List<LessonCharge>,
    val payments: List<Payment>,
    val ledger: List<LedgerEntry>,
)
