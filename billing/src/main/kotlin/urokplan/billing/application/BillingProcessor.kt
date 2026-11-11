package urokplan.billing.application

import urokplan.billing.domain.Balance
import urokplan.billing.domain.ChargeStatus
import urokplan.billing.domain.LedgerEntry
import urokplan.billing.domain.LedgerEntryType
import urokplan.billing.domain.LessonCharge
import urokplan.scheduling.application.LessonReference
import urokplan.scheduling.application.ParticipantReference
import urokplan.scheduling.domain.AttendanceStatus
import urokplan.scheduling.domain.LessonStatus
import java.time.Clock
import java.util.UUID

internal class BillingProcessor(
    private val repository: BillingRepository,
    private val clock: Clock,
) {
    fun reconcileAndCover(
        courseId: UUID,
        studentId: UUID,
        lessons: List<LessonReference>,
        actorId: UUID? = null,
    ): Balance {
        var balance = repository.lockBalance(courseId, studentId, clock.instant())
        lessons.sortedWith(compareBy<LessonReference>({ it.startsAt }, { it.id })).forEach { lesson ->
            val participant = lesson.participants.singleOrNull { it.studentId == studentId } ?: return@forEach
            balance = reconcileLesson(balance, lesson, participant, actorId)
        }
        return coverOpenCharges(balance, actorId)
    }

    private fun reconcileLesson(
        balance: Balance,
        lesson: LessonReference,
        participant: ParticipantReference,
        actorId: UUID?,
    ): Balance {
        if (lesson.endsAt > clock.instant()) return balance
        val payable = lesson.status == LessonStatus.SCHEDULED &&
            participant.attendance != AttendanceStatus.EXCUSED_ABSENCE
        val existing = repository.findCharge(lesson.id, participant.studentId)
        if (existing == null) {
            repository.createCharge(
                LessonCharge(
                    lessonId = lesson.id,
                    studentAccountId = participant.studentId,
                    courseId = lesson.courseId,
                    lessonStartsAt = lesson.startsAt,
                    lessonEndsAt = lesson.endsAt,
                    status = if (payable) ChargeStatus.OPEN else ChargeStatus.WAIVED,
                    createdAt = clock.instant(),
                    waivedAt = if (payable) null else clock.instant(),
                ),
            )
            return balance
        }
        if (payable && existing.status == ChargeStatus.WAIVED) {
            repository.updateCharge(existing.copy(status = ChargeStatus.OPEN, coveredAt = null, waivedAt = null))
            return balance
        }
        if (!payable && existing.status == ChargeStatus.OPEN) {
            repository.updateCharge(existing.copy(status = ChargeStatus.WAIVED, waivedAt = clock.instant()))
            return balance
        }
        if (!payable && existing.status == ChargeStatus.COVERED) {
            val now = clock.instant()
            repository.updateCharge(existing.copy(status = ChargeStatus.WAIVED, waivedAt = now))
            repository.addLedgerEntry(
                LedgerEntry(
                    UUID.randomUUID(), existing.courseId, existing.studentAccountId,
                    LedgerEntryType.REFUND, 1, null, existing.lessonId, now, actorId,
                    "charge_waived",
                ),
            )
            return repository.updateBalance(
                balance.courseId,
                balance.studentAccountId,
                balance.availableUnits + 1,
                now,
            )
        }
        return balance
    }

    private fun coverOpenCharges(balance: Balance, actorId: UUID?): Balance {
        var available = balance.availableUnits
        if (available == 0) return balance
        val now = clock.instant()
        repository.listOpenChargesForUpdate(balance.courseId, balance.studentAccountId)
            .take(available)
            .forEach { charge ->
                repository.updateCharge(charge.copy(status = ChargeStatus.COVERED, coveredAt = now))
                repository.addLedgerEntry(
                    LedgerEntry(
                        UUID.randomUUID(), charge.courseId, charge.studentAccountId,
                        LedgerEntryType.CONSUMPTION, -1, null, charge.lessonId, now, actorId,
                        "lesson_charge_covered",
                    ),
                )
                available--
            }
        return if (available == balance.availableUnits) {
            balance
        } else {
            repository.updateBalance(balance.courseId, balance.studentAccountId, available, now)
        }
    }
}
