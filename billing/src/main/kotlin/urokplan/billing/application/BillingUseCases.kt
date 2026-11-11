package urokplan.billing.application

import java.util.UUID

enum class MockPaymentOutcome {
    SUCCESS,
    FAILURE,
}

interface BillingUseCases {
    fun createPayment(actorId: UUID, command: CreatePaymentCommand): PaymentView
    fun getPayment(actorId: UUID, paymentId: UUID): PaymentView
    fun getSummary(actorId: UUID, courseId: UUID, studentPublicId: String): PaymentSummaryView
    fun preview(actorId: UUID, command: PaymentPreviewCommand): PaymentPreviewView
}

interface PaymentResultHandler {
    fun processPaymentResult(paymentId: UUID): PaymentView
}

interface MockPaymentUseCases {
    fun resolvePayment(providerPaymentId: String, outcome: MockPaymentOutcome): PaymentView
}

enum class BillingErrorKind {
    BAD_REQUEST,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
}

class BillingException(
    val errorCode: String,
    val kind: BillingErrorKind,
) : RuntimeException(errorCode)
