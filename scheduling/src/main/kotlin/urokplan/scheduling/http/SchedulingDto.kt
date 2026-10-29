package urokplan.scheduling.http

import kotlinx.serialization.Serializable
import urokplan.identity.application.AccountReference
import urokplan.scheduling.application.CreateLessonCommand
import urokplan.scheduling.application.CreateSeriesCommand
import urokplan.scheduling.application.LessonParticipantView
import urokplan.scheduling.application.LessonView
import urokplan.scheduling.application.ParticipantPriceCommand
import urokplan.scheduling.application.RescheduleLessonCommand
import urokplan.scheduling.application.SeriesView
import urokplan.scheduling.application.UpdateAttendanceCommand
import urokplan.scheduling.domain.AttendanceStatus
import urokplan.scheduling.domain.ChangeScope
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Serializable
data class ParticipantPriceRequest(
    val studentPublicId: String,
    val priceMinor: Long? = null,
)

@Serializable
data class CreateLessonRequest(
    val courseId: String,
    val groupId: String? = null,
    val startsAt: String,
    val durationMinutes: Int,
    val participants: List<ParticipantPriceRequest> = emptyList(),
    val callLink: String? = null,
)

@Serializable
data class CreateSeriesRequest(
    val courseId: String,
    val groupId: String? = null,
    val localTime: String,
    val zoneId: String,
    val daysOfWeek: Set<String>,
    val startDate: String,
    val endDate: String,
    val durationMinutes: Int,
    val participants: List<ParticipantPriceRequest> = emptyList(),
    val callLink: String? = null,
)

@Serializable
data class RescheduleLessonRequest(
    val startsAt: String,
    val durationMinutes: Int,
    val scope: String,
)

@Serializable
data class CancelLessonRequest(val scope: String)

@Serializable
data class UpdateAttendanceRequest(
    val status: String,
    val reason: String? = null,
)

@Serializable
data class SchedulingAccountResponse(
    val publicId: String,
    val displayName: String,
    val roles: List<String>,
)

@Serializable
data class LessonParticipantResponse(
    val student: SchedulingAccountResponse,
    val priceMinor: Long,
    val attendance: String,
)

@Serializable
data class LessonResponse(
    val id: String,
    val seriesId: String?,
    val courseId: String,
    val courseTitle: String,
    val groupId: String?,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val callLink: String?,
    val participants: List<LessonParticipantResponse>,
)

@Serializable
data class SeriesResponse(
    val id: String,
    val courseId: String,
    val localTime: String,
    val zoneId: String,
    val daysOfWeek: List<String>,
    val startDate: String,
    val endDate: String,
    val durationMinutes: Int,
    val status: String,
    val lessonCount: Int,
)

@Serializable
data class SchedulingErrorResponse(val code: String)

internal fun CreateLessonRequest.toCommand() = CreateLessonCommand(
    courseId = UUID.fromString(courseId),
    groupId = groupId?.let(UUID::fromString),
    startsAt = Instant.parse(startsAt),
    durationMinutes = durationMinutes,
    participants = participants.map { ParticipantPriceCommand(it.studentPublicId, it.priceMinor) },
    callLink = callLink,
)

internal fun CreateSeriesRequest.toCommand() = CreateSeriesCommand(
    courseId = UUID.fromString(courseId),
    groupId = groupId?.let(UUID::fromString),
    localTime = LocalTime.parse(localTime),
    zoneId = zoneId,
    daysOfWeek = daysOfWeek.mapTo(linkedSetOf(), DayOfWeek::valueOf),
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    durationMinutes = durationMinutes,
    participants = participants.map { ParticipantPriceCommand(it.studentPublicId, it.priceMinor) },
    callLink = callLink,
)

internal fun RescheduleLessonRequest.toCommand() = RescheduleLessonCommand(
    Instant.parse(startsAt), durationMinutes, ChangeScope.valueOf(scope),
)

internal fun UpdateAttendanceRequest.toCommand() = UpdateAttendanceCommand(
    AttendanceStatus.valueOf(status), reason,
)

internal fun LessonView.toResponse() = LessonResponse(
    id.toString(), seriesId?.toString(), courseId.toString(), courseTitle, groupId?.toString(),
    startsAt.toString(), endsAt.toString(), status.name, callLink,
    participants.map(LessonParticipantView::toResponse),
)

internal fun SeriesView.toResponse() = SeriesResponse(
    id.toString(), courseId.toString(), localTime.toString(), zoneId,
    daysOfWeek.sortedBy { it.value }.map { it.name }, startDate.toString(), endDate.toString(),
    durationMinutes, status.name, lessonCount,
)

internal fun LessonParticipantView.toResponse() = LessonParticipantResponse(
    student.toResponse(), priceMinor, attendance.name,
)

private fun AccountReference.toResponse() = SchedulingAccountResponse(
    publicId, displayName, roles.map { it.name }.sorted(),
)
