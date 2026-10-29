package urokplan.scheduling.application

import urokplan.courses.application.CourseReference
import urokplan.courses.application.CoursesApi
import urokplan.courses.application.EnrollmentReference
import urokplan.database.TransactionRunner
import urokplan.identity.application.AccountReference
import urokplan.identity.application.IdentityApi
import urokplan.relationships.application.RelationshipsApi
import urokplan.scheduling.domain.AttendanceChange
import urokplan.scheduling.domain.AttendanceStatus
import urokplan.scheduling.domain.ChangeScope
import urokplan.scheduling.domain.Lesson
import urokplan.scheduling.domain.LessonParticipant
import urokplan.scheduling.domain.LessonSeries
import urokplan.scheduling.domain.LessonStatus
import urokplan.scheduling.domain.SeriesStatus
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class SchedulingService(
    private val transactions: TransactionRunner,
    private val repository: SchedulingRepository,
    private val identity: IdentityApi,
    private val relationships: RelationshipsApi,
    private val courses: CoursesApi,
    private val billing: AttendanceBillingUpdater,
    private val clock: Clock,
) : SchedulingUseCases, SchedulingApi {
    override fun createLesson(actorId: UUID, command: CreateLessonCommand): LessonView {
        validateDuration(command.durationMinutes)
        val course = requireOwnedCourse(actorId, command.courseId)
        val participants = resolveParticipants(course, command.groupId, command.participants)
        val now = clock.instant()
        if (command.startsAt <= now) badRequest("lesson_must_be_future")
        val lesson = Lesson(
            id = UUID.randomUUID(),
            seriesId = null,
            courseId = course.id,
            teacherAccountId = actorId,
            groupId = command.groupId,
            startsAt = command.startsAt,
            endsAt = command.startsAt.plus(command.durationMinutes.toLong(), ChronoUnit.MINUTES),
            status = LessonStatus.SCHEDULED,
            callLink = effectiveCallLink(command.callLink, command.groupId, course),
            createdAt = now,
            updatedAt = now,
        )
        transactions.inTransaction {
            lockAndCheck(listOf(lesson to participants))
            repository.createLesson(lesson, participants.forLesson(lesson.id))
        }
        return lesson.toView(participants.forLesson(lesson.id), course, actorId, emptySet())
    }

    override fun createSeries(actorId: UUID, command: CreateSeriesCommand): SeriesView {
        validateDuration(command.durationMinutes)
        val zone = runCatching { ZoneId.of(command.zoneId) }.getOrNull()
            ?: badRequest("invalid_zone_id")
        if (command.daysOfWeek.isEmpty()) badRequest("days_of_week_required")
        if (command.endDate < command.startDate ||
            ChronoUnit.DAYS.between(command.startDate, command.endDate) > MAX_SERIES_DAYS
        ) {
            badRequest("invalid_series_period")
        }
        val course = requireOwnedCourse(actorId, command.courseId)
        val participants = resolveParticipants(course, command.groupId, command.participants)
        val starts = generateOccurrences(command, zone).filter { it > clock.instant() }
        if (starts.isEmpty()) badRequest("series_has_no_future_lessons")
        val now = clock.instant()
        val series = LessonSeries(
            id = UUID.randomUUID(),
            courseId = course.id,
            teacherAccountId = actorId,
            groupId = command.groupId,
            localTime = command.localTime,
            zoneId = zone.id,
            daysOfWeek = command.daysOfWeek,
            startDate = command.startDate,
            endDate = command.endDate,
            durationMinutes = command.durationMinutes,
            callLink = effectiveCallLink(command.callLink, command.groupId, course),
            status = SeriesStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        val lessons = starts.map { start ->
            Lesson(
                id = UUID.randomUUID(),
                seriesId = series.id,
                courseId = course.id,
                teacherAccountId = actorId,
                groupId = command.groupId,
                startsAt = start,
                endsAt = start.plus(command.durationMinutes.toLong(), ChronoUnit.MINUTES),
                status = LessonStatus.SCHEDULED,
                callLink = series.callLink,
                createdAt = now,
                updatedAt = now,
            )
        }
        transactions.inTransaction {
            lockAndCheck(lessons.map { it to participants })
            repository.createSeries(series)
            lessons.forEach { repository.createLesson(it, participants.forLesson(it.id)) }
        }
        return series.toView(lessons.size)
    }

    override fun listLessons(actorId: UUID, from: Instant, to: Instant, courseId: UUID?): List<LessonView> {
        if (to <= from) badRequest("invalid_date_range")
        val childIds = relationships.findActiveGuardianStudentIds(actorId)
        val lessons = transactions.inTransaction {
            repository.listLessons(actorId, childIds, from, to, courseId).map { lesson ->
                lesson to repository.findParticipants(lesson.id)
            }
        }
        return lessons.map { (lesson, participants) ->
            val course = courses.findCourse(lesson.courseId) ?: error("Lesson references a missing course")
            lesson.toView(participants, course, actorId, childIds)
        }
    }

    override fun getLesson(actorId: UUID, lessonId: UUID): LessonView {
        val childIds = relationships.findActiveGuardianStudentIds(actorId)
        val (lesson, participants) = transactions.inTransaction {
            val lesson = repository.findLesson(lessonId) ?: notFound("lesson_not_found")
            lesson to repository.findParticipants(lessonId)
        }
        if (!canAccess(lesson, participants, actorId, childIds)) notFound("lesson_not_found")
        val course = courses.findCourse(lesson.courseId) ?: error("Lesson references a missing course")
        return lesson.toView(participants, course, actorId, childIds)
    }

    override fun rescheduleLesson(
        actorId: UUID,
        lessonId: UUID,
        command: RescheduleLessonCommand,
    ): List<LessonView> {
        validateDuration(command.durationMinutes)
        val initial = transactions.inTransaction { repository.findLesson(lessonId) }
            ?: notFound("lesson_not_found")
        requireOwnedCourse(actorId, initial.courseId)
        if (initial.status != LessonStatus.SCHEDULED) conflict("lesson_cancelled")
        if (initial.startsAt <= clock.instant() || command.startsAt <= clock.instant()) {
            conflict("only_future_lesson_can_change")
        }
        val updatedLessons = transactions.inTransaction {
            val selected = repository.findLessonForUpdate(lessonId) ?: notFound("lesson_not_found")
            val affected = when (command.scope) {
                ChangeScope.THIS -> listOf(selected)
                ChangeScope.THIS_AND_FOLLOWING -> {
                    val seriesId = selected.seriesId ?: badRequest("lesson_has_no_series")
                    repository.listSeriesLessonsFrom(seriesId, selected.startsAt)
                        .filter { it.status == LessonStatus.SCHEDULED }
                }
            }
            val delta = Duration.between(selected.startsAt, command.startsAt)
            val series = selected.seriesId?.let(repository::findSeries)
            val proposals = affected.map { lesson ->
                val start = if (command.scope == ChangeScope.THIS_AND_FOLLOWING && series != null) {
                    val zone = ZoneId.of(series.zoneId)
                    val oldSelectedLocal = selected.startsAt.atZone(zone)
                    val newSelectedLocal = command.startsAt.atZone(zone)
                    val dayShift = ChronoUnit.DAYS.between(
                        oldSelectedLocal.toLocalDate(),
                        newSelectedLocal.toLocalDate(),
                    )
                    val oldLessonLocal = lesson.startsAt.atZone(zone)
                    ZonedDateTime.of(
                        oldLessonLocal.toLocalDate().plusDays(dayShift),
                        newSelectedLocal.toLocalTime(),
                        zone,
                    ).toInstant()
                } else {
                    lesson.startsAt.plus(delta)
                }
                lesson.copy(
                    startsAt = start,
                    endsAt = start.plus(command.durationMinutes.toLong(), ChronoUnit.MINUTES),
                    updatedAt = clock.instant(),
                )
            }
            val participantMap = affected.associate { it.id to repository.findParticipants(it.id) }
            lockAndCheck(
                proposals.map { lesson ->
                    lesson to participantMap.getValue(lesson.id).map {
                        ResolvedParticipant(it.studentAccountId, it.priceMinor)
                    }
                },
                affected.mapTo(mutableSetOf()) { it.id },
            )
            proposals.forEach { repository.updateLessonTime(it.id, it.startsAt, it.endsAt, it.updatedAt) }
            if (command.scope == ChangeScope.THIS_AND_FOLLOWING) {
                updateSeriesRule(selected, command)
            }
            proposals.map { it to participantMap.getValue(it.id) }
        }
        val course = courses.findCourse(initial.courseId) ?: error("Lesson references a missing course")
        return updatedLessons.map { (lesson, participants) ->
            lesson.toView(participants, course, actorId, emptySet())
        }
    }

    override fun cancelLesson(actorId: UUID, lessonId: UUID, scope: ChangeScope): List<LessonView> {
        val initial = transactions.inTransaction { repository.findLesson(lessonId) }
            ?: notFound("lesson_not_found")
        val course = requireOwnedCourse(actorId, initial.courseId)
        if (initial.startsAt <= clock.instant()) conflict("only_future_lesson_can_cancel")
        val cancelled = transactions.inTransaction {
            val selected = repository.findLessonForUpdate(lessonId) ?: notFound("lesson_not_found")
            if (selected.status != LessonStatus.SCHEDULED) conflict("lesson_cancelled")
            val affected = when (scope) {
                ChangeScope.THIS -> listOf(selected)
                ChangeScope.THIS_AND_FOLLOWING -> {
                    val seriesId = selected.seriesId ?: badRequest("lesson_has_no_series")
                    repository.listSeriesLessonsFrom(seriesId, selected.startsAt)
                        .filter { it.status == LessonStatus.SCHEDULED }
                }
            }
            val now = clock.instant()
            repository.cancelLessons(affected.mapTo(mutableSetOf()) { it.id }, now)
            if (scope == ChangeScope.THIS_AND_FOLLOWING) {
                selected.seriesId?.let { id ->
                    repository.findSeries(id)?.let {
                        repository.updateSeries(it.copy(status = SeriesStatus.CANCELLED, updatedAt = now))
                    }
                }
            }
            affected.map { it.copy(status = LessonStatus.CANCELLED, updatedAt = now) to repository.findParticipants(it.id) }
        }
        return cancelled.map { (lesson, participants) ->
            lesson.toView(participants, course, actorId, emptySet())
        }
    }

    override fun updateAttendance(
        actorId: UUID,
        lessonId: UUID,
        studentPublicId: String,
        command: UpdateAttendanceCommand,
    ): LessonParticipantView {
        val student = identity.findAccountByPublicId(studentPublicId) ?: notFound("account_not_found")
        val lesson = transactions.inTransaction { repository.findLesson(lessonId) }
            ?: notFound("lesson_not_found")
        requireOwnedCourse(actorId, lesson.courseId)
        if (lesson.status == LessonStatus.CANCELLED) conflict("lesson_cancelled")
        if (lesson.endsAt > clock.instant()) conflict("lesson_not_finished")
        val reason = command.reason?.trim()?.takeIf(String::isNotEmpty)
        if (reason != null && reason.length > 500) badRequest("invalid_attendance_reason")
        val participant = transactions.inTransaction {
            val current = repository.findParticipantForUpdate(lessonId, student.id)
                ?: notFound("lesson_participant_not_found")
            if (current.attendance != command.status) {
                val now = clock.instant()
                repository.updateAttendance(lessonId, student.id, command.status, actorId, now)
                repository.addAttendanceChange(
                    AttendanceChange(
                        UUID.randomUUID(), lessonId, student.id, current.attendance,
                        command.status, actorId, now, reason,
                    ),
                )
                val updated = current.copy(
                    attendance = command.status,
                    attendanceUpdatedAt = now,
                    attendanceUpdatedBy = actorId,
                )
                billing.attendanceChanged(lesson.toReference(listOf(updated)), updated.toReference())
                updated
            } else {
                current
            }
        }
        return LessonParticipantView(student, participant.priceMinor, participant.attendance)
    }

    override fun findLesson(id: UUID): LessonReference? = transactions.inTransaction {
        repository.findLesson(id)?.let { lesson ->
            lesson.toReference(repository.findParticipants(lesson.id))
        }
    }

    override fun listParticipantLessons(courseId: UUID, studentId: UUID): List<LessonReference> =
        transactions.inTransaction {
            repository.listParticipantLessons(courseId, studentId).map { lesson ->
                lesson.toReference(
                    repository.findParticipants(lesson.id).filter { it.studentAccountId == studentId },
                )
            }
        }

    private fun resolveParticipants(
        course: CourseReference,
        groupId: UUID?,
        requested: List<ParticipantPriceCommand>,
    ): List<ResolvedParticipant> {
        val requestedAccounts = requested.map { command ->
            val account = identity.findAccountByPublicId(command.studentPublicId)
                ?: notFound("account_not_found")
            account.id to command.priceMinor
        }
        if (requestedAccounts.map { it.first }.toSet().size != requestedAccounts.size) {
            badRequest("duplicate_participant")
        }
        val participantIds = if (groupId != null) {
            val group = courses.findGroup(groupId)?.takeIf { it.courseId == course.id }
                ?: notFound("group_not_found")
            if (requestedAccounts.any { it.first !in group.studentIds }) badRequest("participant_not_in_group")
            group.studentIds
        } else {
            requestedAccounts.mapTo(linkedSetOf()) { it.first }
        }
        if (participantIds.isEmpty()) badRequest("participants_required")
        val enrollments = courses.findActiveEnrollments(course.id).associateBy(EnrollmentReference::studentId)
        val overrides = requestedAccounts.toMap()
        return participantIds.map { studentId ->
            val enrollment = enrollments[studentId] ?: badRequest("active_enrollment_required")
            val price = overrides[studentId] ?: enrollment.priceMinor
            if (price <= 0) badRequest("invalid_price")
            ResolvedParticipant(studentId, price)
        }
    }

    private fun lockAndCheck(
        lessons: List<Pair<Lesson, List<ResolvedParticipant>>>,
        excludedIds: Set<UUID> = emptySet(),
    ) {
        val accountIds = lessons.flatMapTo(mutableSetOf()) { (lesson, participants) ->
            participants.map(ResolvedParticipant::studentId) + lesson.teacherAccountId
        }
        repository.lockSchedules(accountIds)
        lessons.forEach { (lesson, participants) ->
            val participantsAndTeacher = participants.mapTo(mutableSetOf(), ResolvedParticipant::studentId)
                .apply { add(lesson.teacherAccountId) }
            if (repository.hasConflict(participantsAndTeacher, lesson.startsAt, lesson.endsAt, excludedIds)) {
                conflict("schedule_conflict")
            }
        }
        lessons.forEachIndexed { index, (lesson, participants) ->
            val accounts = participants.mapTo(mutableSetOf(), ResolvedParticipant::studentId)
                .apply { add(lesson.teacherAccountId) }
            lessons.drop(index + 1).forEach { (other, otherParticipants) ->
                val otherAccounts = otherParticipants.mapTo(mutableSetOf(), ResolvedParticipant::studentId)
                    .apply { add(other.teacherAccountId) }
                if (accounts.any { it in otherAccounts } &&
                    lesson.startsAt < other.endsAt && other.startsAt < lesson.endsAt
                ) {
                    conflict("schedule_conflict")
                }
            }
        }
    }

    private fun updateSeriesRule(selected: Lesson, command: RescheduleLessonCommand) {
        val seriesId = selected.seriesId ?: return
        val series = repository.findSeries(seriesId) ?: return
        val zone = ZoneId.of(series.zoneId)
        val oldLocal = selected.startsAt.atZone(zone)
        val newLocal = command.startsAt.atZone(zone)
        val dayShift = ChronoUnit.DAYS.between(oldLocal.toLocalDate(), newLocal.toLocalDate()).toInt()
        val shiftedDays = series.daysOfWeek.mapTo(linkedSetOf()) { day ->
            val zeroBased = day.value - 1
            java.time.DayOfWeek.of(Math.floorMod(zeroBased + dayShift, 7) + 1)
        }
        repository.updateSeries(
            series.copy(
                localTime = newLocal.toLocalTime(),
                daysOfWeek = shiftedDays,
                durationMinutes = command.durationMinutes,
                endDate = series.endDate.plusDays(dayShift.toLong()),
                updatedAt = clock.instant(),
            ),
        )
    }

    private fun generateOccurrences(command: CreateSeriesCommand, zone: ZoneId): List<Instant> =
        generateSequence(command.startDate) { date -> date.plusDays(1) }
            .takeWhile { it <= command.endDate }
            .filter { it.dayOfWeek in command.daysOfWeek }
            .map { date -> ZonedDateTime.of(date, command.localTime, zone).toInstant() }
            .toList()

    private fun effectiveCallLink(value: String?, groupId: UUID?, course: CourseReference): String? {
        val explicit = validateCallLink(value)
        if (explicit != null) return explicit
        return groupId?.let(courses::findGroup)?.callLink ?: course.callLink
    }

    private fun validateCallLink(value: String?): String? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val uri = runCatching { URI(normalized) }.getOrNull()
        if (normalized.length > 500 || uri?.scheme !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
            badRequest("invalid_call_link")
        }
        return normalized
    }

    private fun requireOwnedCourse(actorId: UUID, courseId: UUID): CourseReference {
        val course = courses.findCourse(courseId) ?: notFound("course_not_found")
        if (course.teacherId != actorId) forbidden("course_owner_required")
        return course
    }

    private fun validateDuration(minutes: Int) {
        if (minutes !in 1..1440) badRequest("invalid_duration")
    }

    private fun canAccess(
        lesson: Lesson,
        participants: List<LessonParticipant>,
        actorId: UUID,
        childIds: Set<UUID>,
    ): Boolean = lesson.teacherAccountId == actorId ||
        participants.any { it.studentAccountId == actorId || it.studentAccountId in childIds }

    private fun Lesson.toView(
        participants: List<LessonParticipant>,
        course: CourseReference,
        actorId: UUID,
        childIds: Set<UUID>,
    ): LessonView {
        val visible = if (teacherAccountId == actorId) {
            participants
        } else {
            participants.filter { it.studentAccountId == actorId || it.studentAccountId in childIds }
        }
        val accounts = identity.findAccountsByIds(visible.mapTo(mutableSetOf()) { it.studentAccountId })
        return LessonView(
            id, seriesId, courseId, course.title, groupId, startsAt, endsAt, status, callLink,
            visible.map { participant ->
                LessonParticipantView(
                    accounts.getValue(participant.studentAccountId),
                    participant.priceMinor,
                    participant.attendance,
                )
            },
        )
    }

    private fun LessonSeries.toView(lessonCount: Int) = SeriesView(
        id, courseId, localTime, zoneId, daysOfWeek, startDate, endDate,
        durationMinutes, status, lessonCount,
    )

    private fun List<ResolvedParticipant>.forLesson(lessonId: UUID) = map {
        LessonParticipant(lessonId, it.studentId, it.priceMinor, AttendanceStatus.UNKNOWN)
    }

    private fun Lesson.toReference(participants: List<LessonParticipant>) = LessonReference(
        id, courseId, teacherAccountId, startsAt, endsAt, status,
        participants.map { it.toReference() },
    )

    private fun LessonParticipant.toReference() =
        ParticipantReference(studentAccountId, priceMinor, attendance)

    private fun badRequest(code: String): Nothing = throw SchedulingException(code, SchedulingErrorKind.BAD_REQUEST)
    private fun forbidden(code: String): Nothing = throw SchedulingException(code, SchedulingErrorKind.FORBIDDEN)
    private fun notFound(code: String): Nothing = throw SchedulingException(code, SchedulingErrorKind.NOT_FOUND)
    private fun conflict(code: String): Nothing = throw SchedulingException(code, SchedulingErrorKind.CONFLICT)

    private data class ResolvedParticipant(val studentId: UUID, val priceMinor: Long)

    private companion object {
        const val MAX_SERIES_DAYS = 366L
    }
}
