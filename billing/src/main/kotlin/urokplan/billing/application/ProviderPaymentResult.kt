package urokplan.billing.application

data class ProviderPaymentResult(
    val status: ProviderPaymentStatus,
    val failureCode: String? = null,
)
