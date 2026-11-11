package urokplan.billing.application

import java.util.UUID

data class PaymentPreviewCommand(
    val courseId: UUID,
    val studentPublicId: String,
    val units: Int,
)
