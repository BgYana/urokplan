package urokplan.scheduling.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

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
