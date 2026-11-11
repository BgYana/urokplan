package urokplan.billing.application

enum class ProviderPaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
}

interface PaymentProvider {
    val name: String
    fun createPayment(command: CreateProviderPayment): ProviderPayment
    fun getPaymentResult(providerPaymentId: String): ProviderPaymentResult
}

interface MockPaymentProviderControl {
    fun setPaymentResult(providerPaymentId: String, outcome: MockPaymentOutcome)
}
