package urokplan.scheduling.application

import urokplan.scheduling.domain.ChangeScope
import java.time.Instant
import java.util.UUID

interface SchedulingUseCases {
    fun createLesson(actorId: UUID, command: CreateLessonCommand): LessonView
    fun createSeries(actorId: UUID, command: CreateSeriesCommand): SeriesView
    fun listLessons(actorId: UUID, from: Instant, to: Instant, courseId: UUID?): List<LessonView>
    fun getLesson(actorId: UUID, lessonId: UUID): LessonView
    fun rescheduleLesson(actorId: UUID, lessonId: UUID, command: RescheduleLessonCommand): List<LessonView>
    fun cancelLesson(actorId: UUID, lessonId: UUID, scope: ChangeScope): List<LessonView>
    fun updateAttendance(
        actorId: UUID,
        lessonId: UUID,
        studentPublicId: String,
        command: UpdateAttendanceCommand,
    ): LessonParticipantView
}

interface SchedulingApi {
    fun findLesson(id: UUID): LessonReference?
    fun listParticipantLessons(courseId: UUID, studentId: UUID): List<LessonReference>
}

interface AttendanceBillingUpdater {
    fun attendanceChanged(lesson: LessonReference, participant: ParticipantReference)
}

enum class SchedulingErrorKind {
    BAD_REQUEST,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
}

class SchedulingException(
    val errorCode: String,
    val kind: SchedulingErrorKind,
) : RuntimeException(errorCode)
