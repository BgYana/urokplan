package urokplan.courses.infrastructure

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import urokplan.courses.application.CourseRepository
import urokplan.courses.application.DuplicateActiveMembershipException
import urokplan.courses.domain.Course
import urokplan.courses.domain.Enrollment
import urokplan.courses.domain.GroupMembership
import urokplan.courses.domain.ParticipationStatus
import urokplan.courses.domain.StudyGroup
import java.time.Instant
import java.util.UUID

class ExposedCourseRepository : CourseRepository {
    override fun createCourse(course: Course): Course {
        CoursesTable.insert {
            it[id] = course.id
            it[teacherAccountId] = course.teacherAccountId
            it[title] = course.title
            it[subject] = course.subject
            it[format] = course.format
            it[callLink] = course.callLink
            it[createdAt] = course.createdAt
            it[updatedAt] = course.updatedAt
        }
        return course
    }

    override fun findCourse(id: UUID): Course? =
        CoursesTable.selectAll().where { CoursesTable.id eq id }.singleOrNull()?.toCourse()

    override fun findCourseForUpdate(id: UUID): Course? =
        CoursesTable.selectAll().where { CoursesTable.id eq id }.forUpdate().singleOrNull()?.toCourse()

    override fun listAccessibleCourses(accountId: UUID, childIds: Set<UUID>): List<Course> {
        val studentIds = childIds + accountId
        val enrolledCourseIds = EnrollmentsTable.select(EnrollmentsTable.courseId).where {
            (EnrollmentsTable.studentAccountId inList studentIds) and
                (EnrollmentsTable.status eq ParticipationStatus.ACTIVE)
        }
        return CoursesTable.selectAll().where {
            (CoursesTable.teacherAccountId eq accountId) or (CoursesTable.id inSubQuery enrolledCourseIds)
        }.orderBy(CoursesTable.createdAt).map(ResultRow::toCourse)
    }

    override fun updateCourse(course: Course): Course {
        CoursesTable.update({ CoursesTable.id eq course.id }) {
            it[title] = course.title
            it[subject] = course.subject
            it[callLink] = course.callLink
            it[updatedAt] = course.updatedAt
        }
        return course
    }

    override fun findEnrollment(courseId: UUID, studentId: UUID): Enrollment? =
        EnrollmentsTable.selectAll().where {
            (EnrollmentsTable.courseId eq courseId) and
                (EnrollmentsTable.studentAccountId eq studentId)
        }.singleOrNull()?.toEnrollment()

    override fun listEnrollments(courseId: UUID): List<Enrollment> =
        EnrollmentsTable.selectAll().where { EnrollmentsTable.courseId eq courseId }
            .orderBy(EnrollmentsTable.enrolledAt)
            .map(ResultRow::toEnrollment)

    override fun saveEnrollment(enrollment: Enrollment): Enrollment {
        val updated = EnrollmentsTable.update({
            (EnrollmentsTable.courseId eq enrollment.courseId) and
                (EnrollmentsTable.studentAccountId eq enrollment.studentAccountId)
        }) {
            it[priceMinor] = enrollment.priceMinor
            it[status] = enrollment.status
            it[endedAt] = enrollment.endedAt
        }
        if (updated == 0) {
            EnrollmentsTable.insert {
                it[courseId] = enrollment.courseId
                it[studentAccountId] = enrollment.studentAccountId
                it[priceMinor] = enrollment.priceMinor
                it[status] = enrollment.status
                it[enrolledAt] = enrollment.enrolledAt
                it[endedAt] = enrollment.endedAt
            }
        }
        return enrollment
    }

    override fun deactivateEnrollment(courseId: UUID, studentId: UUID, endedAt: Instant) {
        EnrollmentsTable.update({
            (EnrollmentsTable.courseId eq courseId) and
                (EnrollmentsTable.studentAccountId eq studentId)
        }) {
            it[status] = ParticipationStatus.INACTIVE
            it[EnrollmentsTable.endedAt] = endedAt
        }
    }

    override fun updateEnrollmentPrice(courseId: UUID, studentId: UUID, priceMinor: Long): Enrollment {
        EnrollmentsTable.update({
            (EnrollmentsTable.courseId eq courseId) and
                (EnrollmentsTable.studentAccountId eq studentId)
        }) { it[EnrollmentsTable.priceMinor] = priceMinor }
        return requireNotNull(findEnrollment(courseId, studentId))
    }

    override fun updateActiveEnrollmentPrices(courseId: UUID, priceMinor: Long) {
        EnrollmentsTable.update({
            (EnrollmentsTable.courseId eq courseId) and
                (EnrollmentsTable.status eq ParticipationStatus.ACTIVE)
        }) { it[EnrollmentsTable.priceMinor] = priceMinor }
    }

    override fun createGroup(group: StudyGroup): StudyGroup {
        GroupsTable.insert {
            it[id] = group.id
            it[courseId] = group.courseId
            it[name] = group.name
            it[callLink] = group.callLink
            it[createdAt] = group.createdAt
            it[updatedAt] = group.updatedAt
        }
        return group
    }

    override fun findGroup(id: UUID): StudyGroup? =
        GroupsTable.selectAll().where { GroupsTable.id eq id }.singleOrNull()?.toGroup()

    override fun listAccessibleGroups(accountId: UUID, childIds: Set<UUID>): List<StudyGroup> {
        val ownedCourseIds = CoursesTable.select(CoursesTable.id)
            .where { CoursesTable.teacherAccountId eq accountId }
        val studentIds = childIds + accountId
        val memberGroupIds = GroupMembershipsTable.select(GroupMembershipsTable.groupId).where {
            (GroupMembershipsTable.studentAccountId inList studentIds) and
                (GroupMembershipsTable.status eq ParticipationStatus.ACTIVE)
        }
        return GroupsTable.selectAll().where {
            (GroupsTable.courseId inSubQuery ownedCourseIds) or (GroupsTable.id inSubQuery memberGroupIds)
        }.orderBy(GroupsTable.createdAt).map(ResultRow::toGroup)
    }

    override fun updateGroup(group: StudyGroup): StudyGroup {
        GroupsTable.update({ GroupsTable.id eq group.id }) {
            it[name] = group.name
            it[callLink] = group.callLink
            it[updatedAt] = group.updatedAt
        }
        return group
    }

    override fun findActiveMembership(courseId: UUID, studentId: UUID): GroupMembership? =
        GroupMembershipsTable.selectAll().where {
            (GroupMembershipsTable.courseId eq courseId) and
                (GroupMembershipsTable.studentAccountId eq studentId) and
                (GroupMembershipsTable.status eq ParticipationStatus.ACTIVE)
        }.singleOrNull()?.toMembership()

    override fun listActiveMemberships(groupId: UUID): List<GroupMembership> =
        GroupMembershipsTable.selectAll().where {
            (GroupMembershipsTable.groupId eq groupId) and
                (GroupMembershipsTable.status eq ParticipationStatus.ACTIVE)
        }.orderBy(GroupMembershipsTable.joinedAt).map(ResultRow::toMembership)

    override fun createMembership(membership: GroupMembership): GroupMembership {
        try {
            GroupMembershipsTable.insert {
                it[id] = membership.id
                it[groupId] = membership.groupId
                it[courseId] = membership.courseId
                it[studentAccountId] = membership.studentAccountId
                it[status] = membership.status
                it[joinedAt] = membership.joinedAt
            }
        } catch (exception: ExposedSQLException) {
            if (exception.sqlState == "23505") throw DuplicateActiveMembershipException()
            throw exception
        }
        return membership
    }

    override fun deactivateMembership(id: UUID, leftAt: Instant) {
        GroupMembershipsTable.update({ GroupMembershipsTable.id eq id }) {
            it[status] = ParticipationStatus.INACTIVE
            it[GroupMembershipsTable.leftAt] = leftAt
        }
    }

    override fun deactivateMemberships(courseId: UUID, studentId: UUID, leftAt: Instant) {
        GroupMembershipsTable.update({
            (GroupMembershipsTable.courseId eq courseId) and
                (GroupMembershipsTable.studentAccountId eq studentId) and
                (GroupMembershipsTable.status eq ParticipationStatus.ACTIVE)
        }) {
            it[status] = ParticipationStatus.INACTIVE
            it[GroupMembershipsTable.leftAt] = leftAt
        }
    }
}

private fun ResultRow.toCourse() = Course(
    id = this[CoursesTable.id],
    teacherAccountId = this[CoursesTable.teacherAccountId],
    title = this[CoursesTable.title],
    subject = this[CoursesTable.subject],
    format = this[CoursesTable.format],
    callLink = this[CoursesTable.callLink],
    createdAt = this[CoursesTable.createdAt],
    updatedAt = this[CoursesTable.updatedAt],
)

private fun ResultRow.toEnrollment() = Enrollment(
    courseId = this[EnrollmentsTable.courseId],
    studentAccountId = this[EnrollmentsTable.studentAccountId],
    priceMinor = this[EnrollmentsTable.priceMinor],
    status = this[EnrollmentsTable.status],
    enrolledAt = this[EnrollmentsTable.enrolledAt],
    endedAt = this[EnrollmentsTable.endedAt],
)

private fun ResultRow.toGroup() = StudyGroup(
    id = this[GroupsTable.id],
    courseId = this[GroupsTable.courseId],
    name = this[GroupsTable.name],
    callLink = this[GroupsTable.callLink],
    createdAt = this[GroupsTable.createdAt],
    updatedAt = this[GroupsTable.updatedAt],
)

private fun ResultRow.toMembership() = GroupMembership(
    id = this[GroupMembershipsTable.id],
    groupId = this[GroupMembershipsTable.groupId],
    courseId = this[GroupMembershipsTable.courseId],
    studentAccountId = this[GroupMembershipsTable.studentAccountId],
    status = this[GroupMembershipsTable.status],
    joinedAt = this[GroupMembershipsTable.joinedAt],
    leftAt = this[GroupMembershipsTable.leftAt],
)
