package urokplan.billing.application

import java.util.UUID

data class CreateProviderPayment(
    val idempotencyKey: UUID,
    val amountMinor: Long,
    val currency: String,
    val description: String,
)
