package urokplan.billing.http

import urokplan.billing.application.CreatePaymentCommand
import urokplan.billing.application.LedgerEntryView
import urokplan.billing.application.MockPaymentOutcome
import urokplan.billing.application.PaymentPreviewView
import urokplan.billing.application.PaymentSummaryView
import urokplan.billing.application.PaymentView
import java.util.UUID

internal fun CreatePaymentRequest.toCommand() = CreatePaymentCommand(
    UUID.fromString(courseId), studentPublicId, units,
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
