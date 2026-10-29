package urokplan.billing.http

import kotlinx.serialization.Serializable
import urokplan.billing.application.CreatePaymentCommand
import urokplan.billing.application.LedgerEntryView
import urokplan.billing.application.MockPaymentOutcome
import urokplan.billing.application.PaymentPreviewCommand
import urokplan.billing.application.PaymentPreviewView
import urokplan.billing.application.PaymentSummaryView
import urokplan.billing.application.PaymentView

@Serializable
data class CreatePaymentRequest(
    val courseId: String,
    val studentPublicId: String,
    val units: Int,
)

@Serializable
data class ResolvePaymentRequest(val outcome: String)

@Serializable
data class MockPaymentResultResponse(val status: String)

@Serializable
data class PaymentPreviewRequest(val units: Int)

@Serializable
data class PaymentResponse(
    val id: String,
    val courseId: String,
    val studentPublicId: String,
    val payerPublicId: String,
    val units: Int,
    val unitPriceMinor: Long,
    val amountMinor: Long,
    val status: String,
    val providerName: String,
    val providerPaymentId: String,
    val confirmationUrl: String?,
    val createdAt: String,
    val resolvedAt: String?,
    val failureCode: String?,
)

@Serializable
data class ChargeResponse(
    val lessonId: String,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val overdue: Boolean,
)

@Serializable
data class LedgerEntryResponse(
    val id: String,
    val type: String,
    val unitsDelta: Int,
    val paymentId: String?,
    val lessonId: String?,
    val createdAt: String,
    val reason: String?,
)

@Serializable
data class PaymentSummaryResponse(
    val courseId: String,
    val studentPublicId: String,
    val availableUnits: Int,
    val charges: List<ChargeResponse>,
    val payments: List<PaymentResponse>,
    val ledger: List<LedgerEntryResponse>,
    val futureCoveredLessonIds: List<String>,
)

@Serializable
data class PaymentPreviewResponse(
    val unitsToBuy: Int,
    val unitPriceMinor: Long,
    val amountMinor: Long,
    val debtLessonIds: List<String>,
    val futureLessonIds: List<String>,
    val remainingUnits: Int,
)

@Serializable
data class BillingErrorResponse(val code: String)

internal fun CreatePaymentRequest.toCommand() = CreatePaymentCommand(
    java.util.UUID.fromString(courseId), studentPublicId, units,
)

internal fun ResolvePaymentRequest.toOutcome() = MockPaymentOutcome.valueOf(outcome)

internal fun PaymentView.toResponse() = PaymentResponse(
    id.toString(), courseId.toString(), studentPublicId, payerPublicId, units,
    unitPriceMinor, amountMinor, status.name, providerName, providerPaymentId, confirmationUrl, createdAt.toString(),
    resolvedAt?.toString(), failureCode,
)

internal fun PaymentSummaryView.toResponse() = PaymentSummaryResponse(
    courseId.toString(),
    studentPublicId,
    availableUnits,
    charges.map {
        ChargeResponse(it.lessonId.toString(), it.startsAt.toString(), it.endsAt.toString(), it.status.name, it.overdue)
    },
    payments.map { it.toResponse() },
    ledger.map { it.toResponse() },
    futureCoveredLessonIds.map { it.toString() },
)

internal fun PaymentPreviewView.toResponse() = PaymentPreviewResponse(
    unitsToBuy,
    unitPriceMinor,
    amountMinor,
    debtLessonIds.map { it.toString() },
    futureLessonIds.map { it.toString() },
    remainingUnits,
)

private fun LedgerEntryView.toResponse() = LedgerEntryResponse(
    id.toString(), type.name, unitsDelta, paymentId?.toString(), lessonId?.toString(),
    createdAt.toString(), reason,
)
