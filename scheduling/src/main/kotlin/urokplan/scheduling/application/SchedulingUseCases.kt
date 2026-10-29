package urokplan.scheduling.application

import urokplan.identity.application.AccountReference
import urokplan.scheduling.domain.AttendanceStatus
import urokplan.scheduling.domain.ChangeScope
import urokplan.scheduling.domain.LessonStatus
import urokplan.scheduling.domain.SeriesStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

data class ParticipantPriceCommand(
    val studentPublicId: String,
    val priceMinor: Long? = null,
)

data class CreateLessonCommand(
    val courseId: UUID,
    val groupId: UUID?,
    val startsAt: Instant,
    val durationMinutes: Int,
    val participants: List<ParticipantPriceCommand>,
    val callLink: String?,
)

data class CreateSeriesCommand(
    val courseId: UUID,
    val groupId: UUID?,
    val localTime: LocalTime,
    val zoneId: String,
    val daysOfWeek: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val durationMinutes: Int,
    val participants: List<ParticipantPriceCommand>,
    val callLink: String?,
)

data class RescheduleLessonCommand(
    val startsAt: Instant,
    val durationMinutes: Int,
    val scope: ChangeScope,
)

data class UpdateAttendanceCommand(
    val status: AttendanceStatus,
    val reason: String?,
)

data class SeriesView(
    val id: UUID,
    val courseId: UUID,
    val localTime: LocalTime,
    val zoneId: String,
    val daysOfWeek: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val durationMinutes: Int,
    val status: SeriesStatus,
    val lessonCount: Int,
)

data class LessonView(
    val id: UUID,
    val seriesId: UUID?,
    val courseId: UUID,
    val courseTitle: String,
    val groupId: UUID?,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: LessonStatus,
    val callLink: String?,
    val participants: List<LessonParticipantView>,
)

data class LessonParticipantView(
    val student: AccountReference,
    val priceMinor: Long,
    val attendance: AttendanceStatus,
)

data class LessonReference(
    val id: UUID,
    val courseId: UUID,
    val teacherId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: LessonStatus,
    val participants: List<ParticipantReference>,
)

data class ParticipantReference(
    val studentId: UUID,
    val priceMinor: Long,
    val attendance: AttendanceStatus,
)

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
