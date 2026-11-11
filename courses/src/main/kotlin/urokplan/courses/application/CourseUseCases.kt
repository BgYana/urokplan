package urokplan.courses.application

import java.util.UUID

interface CourseUseCases {
    fun createCourse(actorId: UUID, command: CreateCourseCommand): CourseView
    fun listCourses(actorId: UUID): List<CourseView>
    fun getCourse(actorId: UUID, courseId: UUID): CourseView
    fun updateCourse(actorId: UUID, courseId: UUID, command: UpdateCourseCommand): CourseView
    fun addEnrollment(actorId: UUID, courseId: UUID, command: AddEnrollmentCommand): EnrollmentView
    fun listEnrollments(actorId: UUID, courseId: UUID): List<EnrollmentView>
    fun updateEnrollmentPrice(actorId: UUID, courseId: UUID, studentPublicId: String, priceMinor: Long): EnrollmentView
    fun updateAllEnrollmentPrices(actorId: UUID, courseId: UUID, priceMinor: Long): List<EnrollmentView>
    fun removeEnrollment(actorId: UUID, courseId: UUID, studentPublicId: String)
    fun createGroup(actorId: UUID, command: CreateGroupCommand): GroupView
    fun listGroups(actorId: UUID): List<GroupView>
    fun getGroup(actorId: UUID, groupId: UUID): GroupView
    fun updateGroup(actorId: UUID, groupId: UUID, command: UpdateGroupCommand): GroupView
    fun addGroupMember(actorId: UUID, groupId: UUID, studentPublicId: String): GroupMemberView
    fun listGroupMembers(actorId: UUID, groupId: UUID): List<GroupMemberView>
    fun removeGroupMember(actorId: UUID, groupId: UUID, studentPublicId: String)
}

interface CoursesApi {
    fun findCourse(id: UUID): CourseReference?
    fun findActiveEnrollment(courseId: UUID, studentId: UUID): EnrollmentReference?
    fun findEnrollment(courseId: UUID, studentId: UUID): EnrollmentReference?
    fun findActiveEnrollments(courseId: UUID): List<EnrollmentReference>
    fun findGroup(id: UUID): GroupReference?
}

enum class CourseErrorKind {
    BAD_REQUEST,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
}

class CourseException(
    val errorCode: String,
    val kind: CourseErrorKind,
) : RuntimeException(errorCode)
