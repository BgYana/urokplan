package urokplan.billing.application

import urokplan.billing.domain.ChargeStatus
import urokplan.billing.domain.LedgerEntryType
import urokplan.billing.domain.PaymentStatus
import java.time.Instant
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

data class CreatePaymentCommand(
    val courseId: UUID,
    val studentPublicId: String,
    val units: Int,
)

data class PaymentPreviewCommand(
    val courseId: UUID,
    val studentPublicId: String,
    val units: Int,
)

data class PaymentView(
    val id: UUID,
    val courseId: UUID,
    val studentPublicId: String,
    val payerPublicId: String,
    val units: Int,
    val unitPriceMinor: Long,
    val amountMinor: Long,
    val status: PaymentStatus,
    val providerName: String,
    val providerPaymentId: String,
    val confirmationUrl: String?,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val failureCode: String?,
)

data class ChargeView(
    val lessonId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: ChargeStatus,
    val overdue: Boolean,
)

data class LedgerEntryView(
    val id: UUID,
    val type: LedgerEntryType,
    val unitsDelta: Int,
    val paymentId: UUID?,
    val lessonId: UUID?,
    val createdAt: Instant,
    val reason: String?,
)

data class PaymentSummaryView(
    val courseId: UUID,
    val studentPublicId: String,
    val availableUnits: Int,
    val charges: List<ChargeView>,
    val payments: List<PaymentView>,
    val ledger: List<LedgerEntryView>,
    val futureCoveredLessonIds: List<UUID>,
)

data class PaymentPreviewView(
    val unitsToBuy: Int,
    val unitPriceMinor: Long,
    val amountMinor: Long,
    val debtLessonIds: List<UUID>,
    val futureLessonIds: List<UUID>,
    val remainingUnits: Int,
)

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
