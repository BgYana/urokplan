package urokplan.billing.application

import urokplan.database.TransactionRunner
import urokplan.scheduling.application.AttendanceBillingUpdater
import urokplan.scheduling.application.LessonReference
import urokplan.scheduling.application.ParticipantReference

internal class BillingAttendanceUpdater(
    private val transactions: TransactionRunner,
    private val processor: BillingProcessor,
) : AttendanceBillingUpdater {
    override fun attendanceChanged(lesson: LessonReference, participant: ParticipantReference) {
        transactions.inTransaction {
            processor.reconcileAndCover(
                lesson.courseId,
                participant.studentId,
                listOf(lesson),
                lesson.teacherId,
            )
        }
    }
}
