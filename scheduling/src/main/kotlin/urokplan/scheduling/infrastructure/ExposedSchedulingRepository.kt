package urokplan.scheduling.infrastructure

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import urokplan.scheduling.application.SchedulingRepository
import urokplan.scheduling.domain.AttendanceChange
import urokplan.scheduling.domain.AttendanceStatus
import urokplan.scheduling.domain.Lesson
import urokplan.scheduling.domain.LessonParticipant
import urokplan.scheduling.domain.LessonSeries
import urokplan.scheduling.domain.LessonStatus
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

class ExposedSchedulingRepository : SchedulingRepository {
    override fun lockSchedules(accountIds: Set<UUID>) {
        accountIds.forEach { id ->
            ScheduleLocksTable.insertIgnore { it[accountId] = id }
        }
        ScheduleLocksTable.selectAll()
            .where { ScheduleLocksTable.accountId inList accountIds.sorted() }
            .orderBy(ScheduleLocksTable.accountId)
            .forUpdate()
            .toList()
    }

    override fun hasConflict(
        accountIds: Set<UUID>,
        startsAt: Instant,
        endsAt: Instant,
        excludedLessonIds: Set<UUID>,
    ): Boolean {
        val participantLessons = LessonParticipantsTable
            .select(LessonParticipantsTable.lessonId)
            .where { LessonParticipantsTable.studentAccountId inList accountIds }
        return LessonsTable.selectAll().where {
            var condition: Op<Boolean> =
                (LessonsTable.status eq LessonStatus.SCHEDULED) and
                    (LessonsTable.startsAt less endsAt) and
                    (LessonsTable.endsAt greater startsAt) and
                    ((LessonsTable.teacherAccountId inList accountIds) or
                        (LessonsTable.id inSubQuery participantLessons))
            if (excludedLessonIds.isNotEmpty()) {
                condition = condition and (LessonsTable.id notInList excludedLessonIds)
            }
            condition
        }.limit(1).any()
    }

    override fun createSeries(series: LessonSeries): LessonSeries {
        LessonSeriesTable.insert {
            it[id] = series.id
            it[courseId] = series.courseId
            it[teacherAccountId] = series.teacherAccountId
            it[groupId] = series.groupId
            it[localTime] = series.localTime
            it[zoneId] = series.zoneId
            it[daysOfWeek] = series.daysOfWeek.serialize()
            it[startDate] = series.startDate
            it[endDate] = series.endDate
            it[durationMinutes] = series.durationMinutes
            it[callLink] = series.callLink
            it[status] = series.status
            it[createdAt] = series.createdAt
            it[updatedAt] = series.updatedAt
        }
        return series
    }

    override fun updateSeries(series: LessonSeries): LessonSeries {
        LessonSeriesTable.update({ LessonSeriesTable.id eq series.id }) {
            it[localTime] = series.localTime
            it[daysOfWeek] = series.daysOfWeek.serialize()
            it[endDate] = series.endDate
            it[durationMinutes] = series.durationMinutes
            it[status] = series.status
            it[updatedAt] = series.updatedAt
        }
        return series
    }

    override fun findSeries(id: UUID): LessonSeries? =
        LessonSeriesTable.selectAll().where { LessonSeriesTable.id eq id }
            .singleOrNull()?.toSeries()

    override fun createLesson(lesson: Lesson, participants: List<LessonParticipant>): Lesson {
        LessonsTable.insert {
            it[id] = lesson.id
            it[seriesId] = lesson.seriesId
            it[courseId] = lesson.courseId
            it[teacherAccountId] = lesson.teacherAccountId
            it[groupId] = lesson.groupId
            it[startsAt] = lesson.startsAt
            it[endsAt] = lesson.endsAt
            it[status] = lesson.status
            it[callLink] = lesson.callLink
            it[createdAt] = lesson.createdAt
            it[updatedAt] = lesson.updatedAt
        }
        participants.forEach { participant ->
            LessonParticipantsTable.insert {
                it[lessonId] = participant.lessonId
                it[studentAccountId] = participant.studentAccountId
                it[priceMinor] = participant.priceMinor
                it[attendance] = participant.attendance
            }
        }
        return lesson
    }

    override fun findLesson(id: UUID): Lesson? =
        LessonsTable.selectAll().where { LessonsTable.id eq id }.singleOrNull()?.toLesson()

    override fun findLessonForUpdate(id: UUID): Lesson? =
        LessonsTable.selectAll().where { LessonsTable.id eq id }.forUpdate().singleOrNull()?.toLesson()

    override fun findParticipants(lessonId: UUID): List<LessonParticipant> =
        LessonParticipantsTable.selectAll().where { LessonParticipantsTable.lessonId eq lessonId }
            .orderBy(LessonParticipantsTable.studentAccountId)
            .map(ResultRow::toParticipant)

    override fun listParticipantLessons(courseId: UUID, studentId: UUID): List<Lesson> {
        val lessonIds = LessonParticipantsTable.select(LessonParticipantsTable.lessonId).where {
            LessonParticipantsTable.studentAccountId eq studentId
        }
        return LessonsTable.selectAll().where {
            (LessonsTable.courseId eq courseId) and (LessonsTable.id inSubQuery lessonIds)
        }.orderBy(LessonsTable.startsAt).map(ResultRow::toLesson)
    }

    override fun listLessons(
        accountId: UUID,
        childIds: Set<UUID>,
        from: Instant,
        to: Instant,
        courseId: UUID?,
    ): List<Lesson> {
        val visibleStudentIds = childIds + accountId
        val participantLessons = LessonParticipantsTable.select(LessonParticipantsTable.lessonId).where {
            LessonParticipantsTable.studentAccountId inList visibleStudentIds
        }
        return LessonsTable.selectAll().where {
            var condition: Op<Boolean> =
                (LessonsTable.startsAt less to) and
                    (LessonsTable.endsAt greater from) and
                    ((LessonsTable.teacherAccountId eq accountId) or
                        (LessonsTable.id inSubQuery participantLessons))
            if (courseId != null) condition = condition and (LessonsTable.courseId eq courseId)
            condition
        }.orderBy(LessonsTable.startsAt).map(ResultRow::toLesson)
    }

    override fun listSeriesLessonsFrom(seriesId: UUID, startsAt: Instant): List<Lesson> =
        LessonsTable.selectAll().where {
            (LessonsTable.seriesId eq seriesId) and (LessonsTable.startsAt greaterEq startsAt)
        }.orderBy(LessonsTable.startsAt).map(ResultRow::toLesson)

    override fun updateLessonTime(id: UUID, startsAt: Instant, endsAt: Instant, updatedAt: Instant) {
        LessonsTable.update({ LessonsTable.id eq id }) {
            it[LessonsTable.startsAt] = startsAt
            it[LessonsTable.endsAt] = endsAt
            it[LessonsTable.updatedAt] = updatedAt
        }
    }

    override fun cancelLessons(ids: Set<UUID>, updatedAt: Instant) {
        LessonsTable.update({ LessonsTable.id inList ids }) {
            it[status] = LessonStatus.CANCELLED
            it[LessonsTable.updatedAt] = updatedAt
        }
    }

    override fun listFutureGroupLessons(groupId: UUID, after: Instant): List<Lesson> =
        LessonsTable.selectAll().where {
            (LessonsTable.groupId eq groupId) and
                (LessonsTable.startsAt greater after) and
                (LessonsTable.status eq LessonStatus.SCHEDULED)
        }.orderBy(LessonsTable.startsAt).map(ResultRow::toLesson)

    override fun addParticipant(participant: LessonParticipant) {
        LessonParticipantsTable.insert {
            it[lessonId] = participant.lessonId
            it[studentAccountId] = participant.studentAccountId
            it[priceMinor] = participant.priceMinor
            it[attendance] = participant.attendance
        }
    }

    override fun removeFutureGroupParticipants(groupId: UUID, studentId: UUID, after: Instant) {
        val futureLessonIds = LessonsTable.select(LessonsTable.id).where {
            (LessonsTable.groupId eq groupId) and
                (LessonsTable.startsAt greater after) and
                (LessonsTable.status eq LessonStatus.SCHEDULED)
        }.map { it[LessonsTable.id] }
        if (futureLessonIds.isEmpty()) return
        LessonParticipantsTable.deleteWhere {
            (LessonParticipantsTable.lessonId inList futureLessonIds) and
                (LessonParticipantsTable.studentAccountId eq studentId)
        }
    }

    override fun removeFutureCourseGroupParticipants(courseId: UUID, studentId: UUID, after: Instant) {
        val futureLessonIds = LessonsTable.select(LessonsTable.id).where {
            (LessonsTable.courseId eq courseId) and
                LessonsTable.groupId.isNotNull() and
                (LessonsTable.startsAt greater after) and
                (LessonsTable.status eq LessonStatus.SCHEDULED)
        }.map { it[LessonsTable.id] }
        if (futureLessonIds.isEmpty()) return
        LessonParticipantsTable.deleteWhere {
            (LessonParticipantsTable.lessonId inList futureLessonIds) and
                (LessonParticipantsTable.studentAccountId eq studentId)
        }
    }

    override fun findParticipantForUpdate(lessonId: UUID, studentId: UUID): LessonParticipant? =
        LessonParticipantsTable.selectAll().where {
            (LessonParticipantsTable.lessonId eq lessonId) and
                (LessonParticipantsTable.studentAccountId eq studentId)
        }.forUpdate().singleOrNull()?.toParticipant()

    override fun updateAttendance(
        lessonId: UUID,
        studentId: UUID,
        status: AttendanceStatus,
        actorId: UUID,
        updatedAt: Instant,
    ) {
        LessonParticipantsTable.update({
            (LessonParticipantsTable.lessonId eq lessonId) and
                (LessonParticipantsTable.studentAccountId eq studentId)
        }) {
            it[attendance] = status
            it[attendanceUpdatedBy] = actorId
            it[attendanceUpdatedAt] = updatedAt
        }
    }

    override fun addAttendanceChange(change: AttendanceChange) {
        AttendanceHistoryTable.insert {
            it[id] = change.id
            it[lessonId] = change.lessonId
            it[studentAccountId] = change.studentAccountId
            it[previousStatus] = change.previousStatus
            it[newStatus] = change.newStatus
            it[changedBy] = change.changedBy
            it[changedAt] = change.changedAt
            it[reason] = change.reason
        }
    }
}

private fun Set<DayOfWeek>.serialize(): String = sortedBy { it.value }.joinToString(",") { it.name }

private fun ResultRow.toSeries() = LessonSeries(
    id = this[LessonSeriesTable.id],
    courseId = this[LessonSeriesTable.courseId],
    teacherAccountId = this[LessonSeriesTable.teacherAccountId],
    groupId = this[LessonSeriesTable.groupId],
    localTime = this[LessonSeriesTable.localTime],
    zoneId = this[LessonSeriesTable.zoneId],
    daysOfWeek = this[LessonSeriesTable.daysOfWeek].split(',').mapTo(linkedSetOf(), DayOfWeek::valueOf),
    startDate = this[LessonSeriesTable.startDate],
    endDate = this[LessonSeriesTable.endDate],
    durationMinutes = this[LessonSeriesTable.durationMinutes],
    callLink = this[LessonSeriesTable.callLink],
    status = this[LessonSeriesTable.status],
    createdAt = this[LessonSeriesTable.createdAt],
    updatedAt = this[LessonSeriesTable.updatedAt],
)

private fun ResultRow.toLesson() = Lesson(
    id = this[LessonsTable.id],
    seriesId = this[LessonsTable.seriesId],
    courseId = this[LessonsTable.courseId],
    teacherAccountId = this[LessonsTable.teacherAccountId],
    groupId = this[LessonsTable.groupId],
    startsAt = this[LessonsTable.startsAt],
    endsAt = this[LessonsTable.endsAt],
    status = this[LessonsTable.status],
    callLink = this[LessonsTable.callLink],
    createdAt = this[LessonsTable.createdAt],
    updatedAt = this[LessonsTable.updatedAt],
)

private fun ResultRow.toParticipant() = LessonParticipant(
    lessonId = this[LessonParticipantsTable.lessonId],
    studentAccountId = this[LessonParticipantsTable.studentAccountId],
    priceMinor = this[LessonParticipantsTable.priceMinor],
    attendance = this[LessonParticipantsTable.attendance],
    attendanceUpdatedAt = this[LessonParticipantsTable.attendanceUpdatedAt],
    attendanceUpdatedBy = this[LessonParticipantsTable.attendanceUpdatedBy],
)
