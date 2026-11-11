package urokplan.scheduling.application

import urokplan.scheduling.domain.SeriesStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

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
