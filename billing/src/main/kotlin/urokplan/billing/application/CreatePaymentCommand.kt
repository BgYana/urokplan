package urokplan.billing.application

import java.util.UUID

data class CreatePaymentCommand(
    val courseId: UUID,
    val studentPublicId: String,
    val units: Int,
)
