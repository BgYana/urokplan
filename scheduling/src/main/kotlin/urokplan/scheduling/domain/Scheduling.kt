package urokplan.scheduling.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class LessonStatus {
    SCHEDULED,
    CANCELLED,
}

enum class SeriesStatus {
    ACTIVE,
    CANCELLED,
}

enum class AttendanceStatus {
    UNKNOWN,
    PRESENT,
    EXCUSED_ABSENCE,
    UNEXCUSED_ABSENCE,
}

enum class ChangeScope {
    THIS,
    THIS_AND_FOLLOWING,
}

data class LessonSeries(
    val id: UUID,
    val courseId: UUID,
    val teacherAccountId: UUID,
    val groupId: UUID?,
    val localTime: LocalTime,
    val zoneId: String,
    val daysOfWeek: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val durationMinutes: Int,
    val callLink: String?,
    val status: SeriesStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Lesson(
    val id: UUID,
    val seriesId: UUID?,
    val courseId: UUID,
    val teacherAccountId: UUID,
    val groupId: UUID?,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: LessonStatus,
    val callLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class LessonParticipant(
    val lessonId: UUID,
    val studentAccountId: UUID,
    val priceMinor: Long,
    val attendance: AttendanceStatus,
    val attendanceUpdatedAt: Instant? = null,
    val attendanceUpdatedBy: UUID? = null,
)

data class AttendanceChange(
    val id: UUID,
    val lessonId: UUID,
    val studentAccountId: UUID,
    val previousStatus: AttendanceStatus,
    val newStatus: AttendanceStatus,
    val changedBy: UUID,
    val changedAt: Instant,
    val reason: String?,
)
