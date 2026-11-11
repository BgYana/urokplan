package urokplan.billing.infrastructure

import urokplan.billing.application.CreateProviderPayment
import urokplan.billing.application.MockPaymentOutcome
import urokplan.billing.application.MockPaymentProviderControl
import urokplan.billing.application.PaymentProvider
import urokplan.billing.application.ProviderPayment
import urokplan.billing.application.ProviderPaymentResult
import urokplan.billing.application.ProviderPaymentStatus
import java.util.concurrent.ConcurrentHashMap

class MockPaymentProvider : PaymentProvider, MockPaymentProviderControl {
    override val name = "mock"

    private val results = ConcurrentHashMap<String, ProviderPaymentResult>()

    override fun createPayment(command: CreateProviderPayment): ProviderPayment {
        val providerPaymentId = "mock-${command.idempotencyKey}"
        results.putIfAbsent(providerPaymentId, ProviderPaymentResult(ProviderPaymentStatus.PENDING))
        return ProviderPayment(
            id = providerPaymentId,
            confirmationUrl = null,
        )
    }

    override fun getPaymentResult(providerPaymentId: String): ProviderPaymentResult =
        results[providerPaymentId] ?: ProviderPaymentResult(
            ProviderPaymentStatus.FAILED,
            "mock_payment_not_found",
        )

    override fun setPaymentResult(providerPaymentId: String, outcome: MockPaymentOutcome) {
        results[providerPaymentId] = when (outcome) {
            MockPaymentOutcome.SUCCESS -> ProviderPaymentResult(ProviderPaymentStatus.SUCCEEDED)
            MockPaymentOutcome.FAILURE -> ProviderPaymentResult(
                ProviderPaymentStatus.FAILED,
                "mock_provider_declined",
            )
        }
    }
}
