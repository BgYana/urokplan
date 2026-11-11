package urokplan.billing.application

import urokplan.billing.domain.Balance
import urokplan.billing.domain.LedgerEntry
import urokplan.billing.domain.LessonCharge
import urokplan.billing.domain.Payment
import urokplan.billing.domain.PaymentStatus
import java.time.Instant
import java.util.UUID

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
