package urokplan.scheduling.application

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

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
