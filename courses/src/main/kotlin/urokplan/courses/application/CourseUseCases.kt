package urokplan.courses.application

import urokplan.courses.domain.CourseFormat
import urokplan.courses.domain.ParticipationStatus
import urokplan.identity.application.AccountReference
import java.time.Instant
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

data class CreateCourseCommand(
    val title: String,
    val subject: String,
    val format: CourseFormat,
    val callLink: String?,
)

data class UpdateCourseCommand(
    val title: String,
    val subject: String,
    val callLink: String?,
)

data class AddEnrollmentCommand(
    val studentPublicId: String,
    val priceMinor: Long,
)

data class CreateGroupCommand(
    val courseId: UUID,
    val name: String,
    val callLink: String?,
)

data class UpdateGroupCommand(
    val name: String,
    val callLink: String?,
)

data class CourseView(
    val id: UUID,
    val teacher: AccountReference,
    val title: String,
    val subject: String,
    val format: CourseFormat,
    val callLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class EnrollmentView(
    val courseId: UUID,
    val student: AccountReference,
    val priceMinor: Long,
    val status: ParticipationStatus,
    val enrolledAt: Instant,
    val endedAt: Instant?,
)

data class GroupView(
    val id: UUID,
    val courseId: UUID,
    val name: String,
    val callLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class GroupMemberView(
    val membershipId: UUID,
    val student: AccountReference,
    val joinedAt: Instant,
)

data class CourseReference(
    val id: UUID,
    val teacherId: UUID,
    val format: CourseFormat,
    val title: String,
    val callLink: String?,
)

data class EnrollmentReference(
    val courseId: UUID,
    val studentId: UUID,
    val priceMinor: Long,
)

data class GroupReference(
    val id: UUID,
    val courseId: UUID,
    val callLink: String?,
    val studentIds: Set<UUID>,
)

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
