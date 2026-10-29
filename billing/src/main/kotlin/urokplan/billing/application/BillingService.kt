package urokplan.billing.application

import urokplan.billing.domain.ChargeStatus
import urokplan.billing.domain.LedgerEntry
import urokplan.billing.domain.LedgerEntryType
import urokplan.billing.domain.Payment
import urokplan.billing.domain.PaymentStatus
import urokplan.courses.application.CourseReference
import urokplan.courses.application.CoursesApi
import urokplan.database.TransactionRunner
import urokplan.identity.application.AccountReference
import urokplan.identity.application.IdentityApi
import urokplan.relationships.application.RelationshipsApi
import urokplan.scheduling.application.LessonReference
import urokplan.scheduling.application.SchedulingApi
import urokplan.scheduling.domain.AttendanceStatus
import urokplan.scheduling.domain.LessonStatus
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

internal class BillingService(
    private val transactions: TransactionRunner,
    private val repository: BillingRepository,
    private val processor: BillingProcessor,
    private val identity: IdentityApi,
    private val relationships: RelationshipsApi,
    private val courses: CoursesApi,
    private val scheduling: SchedulingApi,
    private val paymentProvider: PaymentProvider,
    private val clock: Clock,
) : BillingUseCases, PaymentResultHandler {
    override fun createPayment(actorId: UUID, command: CreatePaymentCommand): PaymentView {
        validateUnits(command.units)
        val student = requireStudent(command.studentPublicId)
        val course = requireCourse(command.courseId)
        requirePaymentAccess(actorId, student.id)
        val enrollment = courses.findActiveEnrollment(course.id, student.id)
            ?: badRequest("active_enrollment_required")
        val amount = calculateAmount(enrollment.priceMinor, command.units)
        val paymentId = UUID.randomUUID()
        val providerPayment = paymentProvider.createPayment(
            CreateProviderPayment(
                idempotencyKey = paymentId,
                amountMinor = amount,
                currency = "RUB",
                description = "${command.units} lesson unit(s) for ${course.id}",
            ),
        )
        val payment = Payment(
            paymentId, course.id, student.id, actorId, command.units,
            enrollment.priceMinor, amount, PaymentStatus.PENDING,
            paymentProvider.name, providerPayment.id, providerPayment.confirmationUrl, clock.instant(),
        )
        transactions.inTransaction { repository.createPayment(payment) }
        return payment.toView(loadAccounts(payment))
    }

    override fun getPayment(actorId: UUID, paymentId: UUID): PaymentView {
        val payment = transactions.inTransaction { repository.findPayment(paymentId) }
            ?: notFound("payment_not_found")
        requirePaymentReadAccess(actorId, payment)
        return payment.toView(loadAccounts(payment))
    }

    override fun processPaymentResult(paymentId: UUID): PaymentView {
        val initial = transactions.inTransaction { repository.findPayment(paymentId) }
            ?: notFound("payment_not_found")
        if (initial.providerName != paymentProvider.name) conflict("payment_provider_unavailable")
        val providerResult = paymentProvider.getPaymentResult(initial.providerPaymentId)
        val lessons = if (providerResult.status == ProviderPaymentStatus.SUCCEEDED) {
            scheduling.listParticipantLessons(initial.courseId, initial.studentAccountId)
        } else {
            emptyList()
        }
        val resolved = transactions.inTransaction {
            val payment = repository.findPaymentForUpdate(paymentId) ?: notFound("payment_not_found")
            val requestedStatus = when (providerResult.status) {
                ProviderPaymentStatus.SUCCEEDED -> PaymentStatus.SUCCEEDED
                ProviderPaymentStatus.FAILED -> PaymentStatus.FAILED
                ProviderPaymentStatus.PENDING -> return@inTransaction payment
            }
            if (payment.status != PaymentStatus.PENDING) {
                if (payment.status == requestedStatus) return@inTransaction payment
                conflict("payment_already_resolved")
            }
            val now = clock.instant()
            if (requestedStatus == PaymentStatus.FAILED) {
                return@inTransaction repository.resolvePayment(
                    payment.id, PaymentStatus.FAILED, now, providerResult.failureCode,
                )
            }
            var balance = processor.reconcileAndCover(
                payment.courseId, payment.studentAccountId, lessons, payment.payerAccountId,
            )
            balance = repository.updateBalance(
                balance.courseId,
                balance.studentAccountId,
                Math.addExact(balance.availableUnits, payment.units),
                now,
            )
            repository.addLedgerEntry(
                LedgerEntry(
                    UUID.randomUUID(), payment.courseId, payment.studentAccountId,
                    LedgerEntryType.TOP_UP, payment.units, payment.id, null, now,
                    payment.payerAccountId, "payment_succeeded",
                ),
            )
            processor.reconcileAndCover(payment.courseId, payment.studentAccountId, emptyList(), payment.payerAccountId)
            repository.resolvePayment(payment.id, PaymentStatus.SUCCEEDED, now, null)
        }
        return resolved.toView(loadAccounts(resolved))
    }

    override fun getSummary(actorId: UUID, courseId: UUID, studentPublicId: String): PaymentSummaryView {
        val student = requireStudent(studentPublicId)
        val course = requireCourse(courseId)
        requireSummaryAccess(actorId, course, student.id)
        val lessons = scheduling.listParticipantLessons(courseId, student.id)
        val balance = synchronize(courseId, student.id, lessons)
        val data = transactions.inTransaction {
            SummaryData(
                repository.listCharges(courseId, student.id),
                repository.listPayments(courseId, student.id),
                repository.listLedgerEntries(courseId, student.id),
            )
        }
        val teacher = requireAccount(course.teacherId)
        val today = clock.instant().atZone(ZoneId.of(teacher.billingTimeZone)).toLocalDate()
        val futureCovered = futureLessons(lessons).take(balance.availableUnits).map(LessonReference::id)
        val accounts = identity.findAccountsByIds(
            data.payments.flatMapTo(mutableSetOf(student.id)) { setOf(it.studentAccountId, it.payerAccountId) },
        )
        return PaymentSummaryView(
            courseId,
            student.publicId,
            balance.availableUnits,
            data.charges.map { charge ->
                ChargeView(
                    charge.lessonId,
                    charge.lessonStartsAt,
                    charge.lessonEndsAt,
                    charge.status,
                    charge.status == ChargeStatus.OPEN &&
                        charge.lessonStartsAt.atZone(ZoneId.of(teacher.billingTimeZone)).toLocalDate() < today,
                )
            },
            data.payments.map { it.toView(accounts) },
            data.ledger.map {
                LedgerEntryView(it.id, it.type, it.unitsDelta, it.paymentId, it.lessonId, it.createdAt, it.reason)
            },
            futureCovered,
        )
    }

    override fun preview(actorId: UUID, command: PaymentPreviewCommand): PaymentPreviewView {
        validateUnits(command.units)
        val student = requireStudent(command.studentPublicId)
        val course = requireCourse(command.courseId)
        requirePaymentAccess(actorId, student.id)
        val enrollment = courses.findActiveEnrollment(course.id, student.id)
            ?: badRequest("active_enrollment_required")
        val lessons = scheduling.listParticipantLessons(course.id, student.id)
        val balance = synchronize(course.id, student.id, lessons)
        val openCharges = transactions.inTransaction {
            repository.listCharges(course.id, student.id).filter { it.status == ChargeStatus.OPEN }
        }
        var units = Math.addExact(balance.availableUnits, command.units)
        val debts = openCharges.take(units).map { it.lessonId }
        units -= debts.size
        val future = futureLessons(lessons).take(units).map(LessonReference::id)
        units -= future.size
        return PaymentPreviewView(
            command.units,
            enrollment.priceMinor,
            calculateAmount(enrollment.priceMinor, command.units),
            debts,
            future,
            units,
        )
    }

    private fun synchronize(courseId: UUID, studentId: UUID, lessons: List<LessonReference>) =
        transactions.inTransaction { processor.reconcileAndCover(courseId, studentId, lessons) }

    private fun futureLessons(lessons: List<LessonReference>): List<LessonReference> = lessons
        .filter { lesson ->
            lesson.status == LessonStatus.SCHEDULED &&
                lesson.startsAt > clock.instant() &&
                lesson.participants.singleOrNull()?.attendance != AttendanceStatus.EXCUSED_ABSENCE
        }
        .sortedWith(compareBy({ it.startsAt }, { it.id }))

    private fun requirePaymentAccess(actorId: UUID, studentId: UUID) {
        if (actorId != studentId && studentId !in relationships.findActiveGuardianStudentIds(actorId)) {
            forbidden("student_or_parent_required")
        }
    }

    private fun requireSummaryAccess(actorId: UUID, course: CourseReference, studentId: UUID) {
        if (courses.findEnrollment(course.id, studentId) == null) {
            notFound("enrollment_not_found")
        }
        if (actorId != course.teacherId && actorId != studentId &&
            studentId !in relationships.findActiveGuardianStudentIds(actorId)
        ) {
            forbidden("payment_access_denied")
        }
    }

    private fun requirePaymentReadAccess(actorId: UUID, payment: Payment) {
        val course = requireCourse(payment.courseId)
        requireSummaryAccess(actorId, course, payment.studentAccountId)
    }

    private fun requireCourse(id: UUID) = courses.findCourse(id) ?: notFound("course_not_found")
    private fun requireStudent(publicId: String) =
        identity.findAccountByPublicId(publicId) ?: notFound("account_not_found")
    private fun requireAccount(id: UUID): AccountReference =
        identity.findAccountsByIds(setOf(id))[id] ?: notFound("account_not_found")

    private fun validateUnits(units: Int) {
        if (units !in 1..MAX_PAYMENT_UNITS) badRequest("invalid_units")
    }

    private fun calculateAmount(price: Long, units: Int): Long =
        runCatching { Math.multiplyExact(price, units.toLong()) }
            .getOrElse { badRequest("payment_amount_too_large") }

    private fun loadAccounts(payment: Payment) =
        identity.findAccountsByIds(setOf(payment.studentAccountId, payment.payerAccountId))

    private fun Payment.toView(accounts: Map<UUID, AccountReference>) = PaymentView(
        id,
        courseId,
        accounts.getValue(studentAccountId).publicId,
        accounts.getValue(payerAccountId).publicId,
        units,
        unitPriceMinor,
        amountMinor,
        status,
        providerName,
        providerPaymentId,
        confirmationUrl,
        createdAt,
        resolvedAt,
        failureCode,
    )

    private fun badRequest(code: String): Nothing = throw BillingException(code, BillingErrorKind.BAD_REQUEST)
    private fun forbidden(code: String): Nothing = throw BillingException(code, BillingErrorKind.FORBIDDEN)
    private fun notFound(code: String): Nothing = throw BillingException(code, BillingErrorKind.NOT_FOUND)
    private fun conflict(code: String): Nothing = throw BillingException(code, BillingErrorKind.CONFLICT)

    private data class SummaryData(
        val charges: List<urokplan.billing.domain.LessonCharge>,
        val payments: List<Payment>,
        val ledger: List<urokplan.billing.domain.LedgerEntry>,
    )

    private companion object {
        const val MAX_PAYMENT_UNITS = 10_000
    }
}
