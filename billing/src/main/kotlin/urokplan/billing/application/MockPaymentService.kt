package urokplan.billing.application

import urokplan.database.TransactionRunner

internal class MockPaymentService(
    private val transactions: TransactionRunner,
    private val repository: BillingRepository,
    private val mockPaymentProvider: MockPaymentProviderControl,
    private val paymentResults: PaymentResultHandler,
    private val providerName: String,
) : MockPaymentUseCases {
    override fun resolvePayment(
        providerPaymentId: String,
        outcome: MockPaymentOutcome,
    ): PaymentView {
        val payment = transactions.inTransaction {
            repository.findPaymentByProviderId(providerName, providerPaymentId)
        }
            ?: throw BillingException("payment_not_found", BillingErrorKind.NOT_FOUND)
        mockPaymentProvider.setPaymentResult(payment.providerPaymentId, outcome)
        return paymentResults.processPaymentResult(payment.id)
    }
}
