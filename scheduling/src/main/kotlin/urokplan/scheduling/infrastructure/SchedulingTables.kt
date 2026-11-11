package urokplan.scheduling.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestamp
import urokplan.scheduling.domain.AttendanceStatus
import urokplan.scheduling.domain.LessonStatus
import urokplan.scheduling.domain.SeriesStatus

internal object LessonSeriesTable : Table("scheduling.lesson_series") {
    val id = uuid("id")
    val courseId = uuid("course_id")
    val teacherAccountId = uuid("teacher_account_id")
    val groupId = uuid("group_id").nullable()
    val localTime = time("local_time")
    val zoneId = varchar("zone_id", 80)
    val daysOfWeek = varchar("days_of_week", 80)
    val startDate = date("start_date")
    val endDate = date("end_date")
    val durationMinutes = integer("duration_minutes")
    val callLink = varchar("call_link", 500).nullable()
    val status = enumerationByName<SeriesStatus>("status", 20)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object LessonsTable : Table("scheduling.lessons") {
    val id = uuid("id")
    val seriesId = uuid("series_id").nullable()
    val courseId = uuid("course_id")
    val teacherAccountId = uuid("teacher_account_id")
    val groupId = uuid("group_id").nullable()
    val startsAt = timestamp("starts_at")
    val endsAt = timestamp("ends_at")
    val status = enumerationByName<LessonStatus>("status", 20)
    val callLink = varchar("call_link", 500).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object LessonParticipantsTable : Table("scheduling.lesson_participants") {
    val lessonId = uuid("lesson_id")
    val studentAccountId = uuid("student_account_id")
    val priceMinor = long("price_minor")
    val attendance = enumerationByName<AttendanceStatus>("attendance", 30)
    val attendanceUpdatedAt = timestamp("attendance_updated_at").nullable()
    val attendanceUpdatedBy = uuid("attendance_updated_by").nullable()
    override val primaryKey = PrimaryKey(lessonId, studentAccountId)
}

internal object AttendanceHistoryTable : Table("scheduling.attendance_history") {
    val id = uuid("id")
    val lessonId = uuid("lesson_id")
    val studentAccountId = uuid("student_account_id")
    val previousStatus = enumerationByName<AttendanceStatus>("previous_status", 30)
    val newStatus = enumerationByName<AttendanceStatus>("new_status", 30)
    val changedBy = uuid("changed_by")
    val changedAt = timestamp("changed_at")
    val reason = varchar("reason", 500).nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object ScheduleLocksTable : Table("scheduling.schedule_locks") {
    val accountId = uuid("account_id")
    override val primaryKey = PrimaryKey(accountId)
}
