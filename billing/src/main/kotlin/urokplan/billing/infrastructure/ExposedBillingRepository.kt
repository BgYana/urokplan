package urokplan.billing.infrastructure

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import urokplan.billing.application.BillingRepository
import urokplan.billing.domain.Balance
import urokplan.billing.domain.ChargeStatus
import urokplan.billing.domain.LedgerEntry
import urokplan.billing.domain.LessonCharge
import urokplan.billing.domain.Payment
import urokplan.billing.domain.PaymentStatus
import java.time.Instant
import java.util.UUID

class ExposedBillingRepository : BillingRepository {
    override fun lockBalance(courseId: UUID, studentId: UUID, now: Instant): Balance {
        BalancesTable.insertIgnore {
            it[BalancesTable.courseId] = courseId
            it[studentAccountId] = studentId
            it[availableUnits] = 0
            it[updatedAt] = now
        }
        return BalancesTable.selectAll().where {
            (BalancesTable.courseId eq courseId) and (BalancesTable.studentAccountId eq studentId)
        }.forUpdate().single().toBalance()
    }

    override fun updateBalance(
        courseId: UUID,
        studentId: UUID,
        availableUnits: Int,
        updatedAt: Instant,
    ): Balance {
        BalancesTable.update({
            (BalancesTable.courseId eq courseId) and (BalancesTable.studentAccountId eq studentId)
        }) {
            it[BalancesTable.availableUnits] = availableUnits
            it[BalancesTable.updatedAt] = updatedAt
        }
        return Balance(courseId, studentId, availableUnits, updatedAt)
    }

    override fun createPayment(payment: Payment): Payment {
        PaymentsTable.insert {
            it[id] = payment.id
            it[courseId] = payment.courseId
            it[studentAccountId] = payment.studentAccountId
            it[payerAccountId] = payment.payerAccountId
            it[units] = payment.units
            it[unitPriceMinor] = payment.unitPriceMinor
            it[amountMinor] = payment.amountMinor
            it[status] = payment.status
            it[providerName] = payment.providerName
            it[providerPaymentId] = payment.providerPaymentId
            it[confirmationUrl] = payment.confirmationUrl
            it[createdAt] = payment.createdAt
        }
        return payment
    }

    override fun findPayment(id: UUID): Payment? =
        PaymentsTable.selectAll().where { PaymentsTable.id eq id }.singleOrNull()?.toPayment()

    override fun findPaymentByProviderId(providerName: String, providerPaymentId: String): Payment? =
        PaymentsTable.selectAll().where {
            (PaymentsTable.providerName eq providerName) and
                (PaymentsTable.providerPaymentId eq providerPaymentId)
        }.singleOrNull()?.toPayment()

    override fun findPaymentForUpdate(id: UUID): Payment? =
        PaymentsTable.selectAll().where { PaymentsTable.id eq id }.forUpdate().singleOrNull()?.toPayment()

    override fun resolvePayment(
        id: UUID,
        status: PaymentStatus,
        resolvedAt: Instant,
        failureCode: String?,
    ): Payment {
        PaymentsTable.update({ PaymentsTable.id eq id }) {
            it[PaymentsTable.status] = status
            it[PaymentsTable.resolvedAt] = resolvedAt
            it[PaymentsTable.failureCode] = failureCode
        }
        return requireNotNull(findPayment(id))
    }

    override fun listPayments(courseId: UUID, studentId: UUID): List<Payment> =
        PaymentsTable.selectAll().where {
            (PaymentsTable.courseId eq courseId) and (PaymentsTable.studentAccountId eq studentId)
        }.orderBy(PaymentsTable.createdAt).map(ResultRow::toPayment)

    override fun findCharge(lessonId: UUID, studentId: UUID): LessonCharge? =
        LessonChargesTable.selectAll().where {
            (LessonChargesTable.lessonId eq lessonId) and
                (LessonChargesTable.studentAccountId eq studentId)
        }.singleOrNull()?.toCharge()

    override fun createCharge(charge: LessonCharge): LessonCharge {
        LessonChargesTable.insert {
            it[lessonId] = charge.lessonId
            it[studentAccountId] = charge.studentAccountId
            it[courseId] = charge.courseId
            it[lessonStartsAt] = charge.lessonStartsAt
            it[lessonEndsAt] = charge.lessonEndsAt
            it[status] = charge.status
            it[createdAt] = charge.createdAt
            it[coveredAt] = charge.coveredAt
            it[waivedAt] = charge.waivedAt
        }
        return charge
    }

    override fun updateCharge(charge: LessonCharge): LessonCharge {
        LessonChargesTable.update({
            (LessonChargesTable.lessonId eq charge.lessonId) and
                (LessonChargesTable.studentAccountId eq charge.studentAccountId)
        }) {
            it[status] = charge.status
            it[coveredAt] = charge.coveredAt
            it[waivedAt] = charge.waivedAt
        }
        return charge
    }

    override fun listCharges(courseId: UUID, studentId: UUID): List<LessonCharge> =
        LessonChargesTable.selectAll().where {
            (LessonChargesTable.courseId eq courseId) and
                (LessonChargesTable.studentAccountId eq studentId)
        }.orderBy(
            LessonChargesTable.lessonStartsAt to SortOrder.ASC,
            LessonChargesTable.lessonId to SortOrder.ASC,
        ).map(ResultRow::toCharge)

    override fun listOpenChargesForUpdate(courseId: UUID, studentId: UUID): List<LessonCharge> =
        LessonChargesTable.selectAll().where {
            (LessonChargesTable.courseId eq courseId) and
                (LessonChargesTable.studentAccountId eq studentId) and
                (LessonChargesTable.status eq ChargeStatus.OPEN)
        }.forUpdate().orderBy(
            LessonChargesTable.lessonStartsAt to SortOrder.ASC,
            LessonChargesTable.lessonId to SortOrder.ASC,
        )
            .map(ResultRow::toCharge)

    override fun addLedgerEntry(entry: LedgerEntry) {
        LedgerEntriesTable.insert {
            it[id] = entry.id
            it[courseId] = entry.courseId
            it[studentAccountId] = entry.studentAccountId
            it[type] = entry.type
            it[unitsDelta] = entry.unitsDelta
            it[paymentId] = entry.paymentId
            it[lessonId] = entry.lessonId
            it[createdAt] = entry.createdAt
            it[actorAccountId] = entry.actorAccountId
            it[reason] = entry.reason
        }
    }

    override fun listLedgerEntries(courseId: UUID, studentId: UUID): List<LedgerEntry> =
        LedgerEntriesTable.selectAll().where {
            (LedgerEntriesTable.courseId eq courseId) and
                (LedgerEntriesTable.studentAccountId eq studentId)
        }.orderBy(
            LedgerEntriesTable.sequenceNumber to SortOrder.ASC,
        ).map(ResultRow::toLedgerEntry)
}

private fun ResultRow.toBalance() = Balance(
    this[BalancesTable.courseId], this[BalancesTable.studentAccountId],
    this[BalancesTable.availableUnits], this[BalancesTable.updatedAt],
)

private fun ResultRow.toPayment() = Payment(
    id = this[PaymentsTable.id],
    courseId = this[PaymentsTable.courseId],
    studentAccountId = this[PaymentsTable.studentAccountId],
    payerAccountId = this[PaymentsTable.payerAccountId],
    units = this[PaymentsTable.units],
    unitPriceMinor = this[PaymentsTable.unitPriceMinor],
    amountMinor = this[PaymentsTable.amountMinor],
    status = this[PaymentsTable.status],
    providerName = this[PaymentsTable.providerName],
    providerPaymentId = this[PaymentsTable.providerPaymentId],
    confirmationUrl = this[PaymentsTable.confirmationUrl],
    createdAt = this[PaymentsTable.createdAt],
    resolvedAt = this[PaymentsTable.resolvedAt],
    failureCode = this[PaymentsTable.failureCode],
)

private fun ResultRow.toCharge() = LessonCharge(
    lessonId = this[LessonChargesTable.lessonId],
    studentAccountId = this[LessonChargesTable.studentAccountId],
    courseId = this[LessonChargesTable.courseId],
    lessonStartsAt = this[LessonChargesTable.lessonStartsAt],
    lessonEndsAt = this[LessonChargesTable.lessonEndsAt],
    status = this[LessonChargesTable.status],
    createdAt = this[LessonChargesTable.createdAt],
    coveredAt = this[LessonChargesTable.coveredAt],
    waivedAt = this[LessonChargesTable.waivedAt],
)

private fun ResultRow.toLedgerEntry() = LedgerEntry(
    id = this[LedgerEntriesTable.id],
    courseId = this[LedgerEntriesTable.courseId],
    studentAccountId = this[LedgerEntriesTable.studentAccountId],
    type = this[LedgerEntriesTable.type],
    unitsDelta = this[LedgerEntriesTable.unitsDelta],
    paymentId = this[LedgerEntriesTable.paymentId],
    lessonId = this[LedgerEntriesTable.lessonId],
    createdAt = this[LedgerEntriesTable.createdAt],
    actorAccountId = this[LedgerEntriesTable.actorAccountId],
    reason = this[LedgerEntriesTable.reason],
)
