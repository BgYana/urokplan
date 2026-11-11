package urokplan.scheduling.application

import urokplan.scheduling.domain.AttendanceChange
import urokplan.scheduling.domain.AttendanceStatus
import urokplan.scheduling.domain.Lesson
import urokplan.scheduling.domain.LessonParticipant
import urokplan.scheduling.domain.LessonSeries
import java.time.Instant
import java.util.UUID

interface SchedulingRepository {
    fun lockSchedules(accountIds: Set<UUID>)
    fun hasConflict(accountIds: Set<UUID>, startsAt: Instant, endsAt: Instant, excludedLessonIds: Set<UUID>): Boolean

    fun createSeries(series: LessonSeries): LessonSeries
    fun updateSeries(series: LessonSeries): LessonSeries
    fun findSeries(id: UUID): LessonSeries?
    fun createLesson(lesson: Lesson, participants: List<LessonParticipant>): Lesson
    fun findLesson(id: UUID): Lesson?
    fun findLessonForUpdate(id: UUID): Lesson?
    fun findParticipants(lessonId: UUID): List<LessonParticipant>
    fun listParticipantLessons(courseId: UUID, studentId: UUID): List<Lesson>
    fun listLessons(
        accountId: UUID,
        childIds: Set<UUID>,
        from: Instant,
        to: Instant,
        courseId: UUID?,
    ): List<Lesson>
    fun listSeriesLessonsFrom(seriesId: UUID, startsAt: Instant): List<Lesson>
    fun updateLessonTime(id: UUID, startsAt: Instant, endsAt: Instant, updatedAt: Instant)
    fun cancelLessons(ids: Set<UUID>, updatedAt: Instant)
    fun listFutureGroupLessons(groupId: UUID, after: Instant): List<Lesson>
    fun addParticipant(participant: LessonParticipant)
    fun removeFutureGroupParticipants(groupId: UUID, studentId: UUID, after: Instant)
    fun removeFutureCourseGroupParticipants(courseId: UUID, studentId: UUID, after: Instant)

    fun findParticipantForUpdate(lessonId: UUID, studentId: UUID): LessonParticipant?
    fun updateAttendance(
        lessonId: UUID,
        studentId: UUID,
        status: AttendanceStatus,
        actorId: UUID,
        updatedAt: Instant,
    )
    fun addAttendanceChange(change: AttendanceChange)
}
