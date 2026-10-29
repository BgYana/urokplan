package urokplan.courses.application

import urokplan.courses.domain.Course
import urokplan.courses.domain.Enrollment
import urokplan.courses.domain.GroupMembership
import urokplan.courses.domain.StudyGroup
import java.time.Instant
import java.util.UUID

interface CourseRepository {
    fun createCourse(course: Course): Course
    fun findCourse(id: UUID): Course?
    fun findCourseForUpdate(id: UUID): Course?
    fun listAccessibleCourses(accountId: UUID, childIds: Set<UUID>): List<Course>
    fun updateCourse(course: Course): Course

    fun findEnrollment(courseId: UUID, studentId: UUID): Enrollment?
    fun listEnrollments(courseId: UUID): List<Enrollment>
    fun saveEnrollment(enrollment: Enrollment): Enrollment
    fun deactivateEnrollment(courseId: UUID, studentId: UUID, endedAt: Instant)
    fun updateEnrollmentPrice(courseId: UUID, studentId: UUID, priceMinor: Long): Enrollment
    fun updateActiveEnrollmentPrices(courseId: UUID, priceMinor: Long)

    fun createGroup(group: StudyGroup): StudyGroup
    fun findGroup(id: UUID): StudyGroup?
    fun listAccessibleGroups(accountId: UUID, childIds: Set<UUID>): List<StudyGroup>
    fun updateGroup(group: StudyGroup): StudyGroup
    fun findActiveMembership(courseId: UUID, studentId: UUID): GroupMembership?
    fun listActiveMemberships(groupId: UUID): List<GroupMembership>
    fun createMembership(membership: GroupMembership): GroupMembership
    fun deactivateMembership(id: UUID, leftAt: Instant)
    fun deactivateMemberships(courseId: UUID, studentId: UUID, leftAt: Instant)
}

class DuplicateActiveMembershipException : RuntimeException()

interface GroupScheduleUpdater {
    fun addStudentToFutureLessons(groupId: UUID, studentId: UUID, priceMinor: Long)
    fun removeStudentFromFutureLessons(groupId: UUID, studentId: UUID)
    fun removeStudentFromCourseFutureGroupLessons(courseId: UUID, studentId: UUID)
}

class GroupScheduleConflictException : RuntimeException()
