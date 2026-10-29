package urokplan.billing.application

import urokplan.billing.domain.Balance
import urokplan.billing.domain.LedgerEntry
import urokplan.billing.domain.LessonCharge
import urokplan.billing.domain.Payment
import urokplan.billing.domain.PaymentStatus
import java.time.Instant
import java.util.UUID

data class CreateProviderPayment(
    val idempotencyKey: UUID,
    val amountMinor: Long,
    val currency: String,
    val description: String,
)

data class ProviderPayment(
    val id: String,
    val confirmationUrl: String?,
)

enum class ProviderPaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
}

data class ProviderPaymentResult(
    val status: ProviderPaymentStatus,
    val failureCode: String? = null,
)

interface PaymentProvider {
    val name: String
    fun createPayment(command: CreateProviderPayment): ProviderPayment
    fun getPaymentResult(providerPaymentId: String): ProviderPaymentResult
}

interface MockPaymentProviderControl {
    fun setPaymentResult(providerPaymentId: String, outcome: MockPaymentOutcome)
}

interface BillingRepository {
    fun lockBalance(courseId: UUID, studentId: UUID, now: Instant): Balance
    fun updateBalance(courseId: UUID, studentId: UUID, availableUnits: Int, updatedAt: Instant): Balance

    fun createPayment(payment: Payment): Payment
    fun findPayment(id: UUID): Payment?
    fun findPaymentByProviderId(providerName: String, providerPaymentId: String): Payment?
    fun findPaymentForUpdate(id: UUID): Payment?
    fun resolvePayment(id: UUID, status: PaymentStatus, resolvedAt: Instant, failureCode: String?): Payment
    fun listPayments(courseId: UUID, studentId: UUID): List<Payment>

    fun findCharge(lessonId: UUID, studentId: UUID): LessonCharge?
    fun createCharge(charge: LessonCharge): LessonCharge
    fun updateCharge(charge: LessonCharge): LessonCharge
    fun listCharges(courseId: UUID, studentId: UUID): List<LessonCharge>
    fun listOpenChargesForUpdate(courseId: UUID, studentId: UUID): List<LessonCharge>

    fun addLedgerEntry(entry: LedgerEntry)
    fun listLedgerEntries(courseId: UUID, studentId: UUID): List<LedgerEntry>
}
