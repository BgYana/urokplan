package urokplan.courses.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import urokplan.courses.domain.CourseFormat
import urokplan.courses.domain.ParticipationStatus

internal object CoursesTable : Table("courses.courses") {
    val id = uuid("id")
    val teacherAccountId = uuid("teacher_account_id")
    val title = varchar("title", 100)
    val subject = varchar("subject", 100)
    val format = enumerationByName<CourseFormat>("format", 20)
    val callLink = varchar("call_link", 500).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object EnrollmentsTable : Table("courses.enrollments") {
    val courseId = uuid("course_id")
    val studentAccountId = uuid("student_account_id")
    val priceMinor = long("price_minor")
    val status = enumerationByName<ParticipationStatus>("status", 20)
    val enrolledAt = timestamp("enrolled_at")
    val endedAt = timestamp("ended_at").nullable()
    override val primaryKey = PrimaryKey(courseId, studentAccountId)
}

internal object GroupsTable : Table("courses.groups") {
    val id = uuid("id")
    val courseId = uuid("course_id")
    val name = varchar("name", 100)
    val callLink = varchar("call_link", 500).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object GroupMembershipsTable : Table("courses.group_memberships") {
    val id = uuid("id")
    val groupId = uuid("group_id")
    val courseId = uuid("course_id")
    val studentAccountId = uuid("student_account_id")
    val status = enumerationByName<ParticipationStatus>("status", 20)
    val joinedAt = timestamp("joined_at")
    val leftAt = timestamp("left_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
