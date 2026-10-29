package urokplan.scheduling.application

import urokplan.courses.application.GroupScheduleConflictException
import urokplan.courses.application.GroupScheduleUpdater
import urokplan.database.TransactionRunner
import urokplan.scheduling.domain.AttendanceStatus
import urokplan.scheduling.domain.LessonParticipant
import java.time.Clock
import java.util.UUID

class SchedulingGroupScheduleUpdater(
    private val transactions: TransactionRunner,
    private val repository: SchedulingRepository,
    private val clock: Clock,
) : GroupScheduleUpdater {
    override fun addStudentToFutureLessons(groupId: UUID, studentId: UUID, priceMinor: Long) {
        transactions.inTransaction {
            val lessons = repository.listFutureGroupLessons(groupId, clock.instant())
            if (lessons.isEmpty()) return@inTransaction
            repository.lockSchedules(setOf(studentId))
            if (lessons.any { lesson ->
                    repository.hasConflict(
                        setOf(studentId), lesson.startsAt, lesson.endsAt, setOf(lesson.id),
                    )
                }
            ) {
                throw GroupScheduleConflictException()
            }
            lessons.forEach { lesson ->
                repository.addParticipant(
                    LessonParticipant(
                        lesson.id,
                        studentId,
                        priceMinor,
                        AttendanceStatus.UNKNOWN,
                    ),
                )
            }
        }
    }

    override fun removeStudentFromFutureLessons(groupId: UUID, studentId: UUID) {
        transactions.inTransaction {
            repository.removeFutureGroupParticipants(groupId, studentId, clock.instant())
        }
    }

    override fun removeStudentFromCourseFutureGroupLessons(courseId: UUID, studentId: UUID) {
        transactions.inTransaction {
            repository.removeFutureCourseGroupParticipants(courseId, studentId, clock.instant())
        }
    }
}
