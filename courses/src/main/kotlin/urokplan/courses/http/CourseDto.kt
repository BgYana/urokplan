package urokplan.courses.http

import kotlinx.serialization.Serializable
import urokplan.courses.application.AddEnrollmentCommand
import urokplan.courses.application.CourseView
import urokplan.courses.application.CreateCourseCommand
import urokplan.courses.application.CreateGroupCommand
import urokplan.courses.application.EnrollmentView
import urokplan.courses.application.GroupMemberView
import urokplan.courses.application.GroupView
import urokplan.courses.application.UpdateCourseCommand
import urokplan.courses.application.UpdateGroupCommand
import urokplan.courses.domain.CourseFormat
import urokplan.identity.application.AccountReference

@Serializable
enum class CourseFormatDto {
    INDIVIDUAL,
    GROUP,
}

@Serializable
data class CreateCourseRequest(
    val title: String,
    val subject: String,
    val format: CourseFormatDto,
    val callLink: String? = null,
)

@Serializable
data class UpdateCourseRequest(
    val title: String,
    val subject: String,
    val callLink: String? = null,
)

@Serializable
data class AddEnrollmentRequest(
    val studentPublicId: String,
    val priceMinor: Long,
)

@Serializable
data class UpdatePriceRequest(val priceMinor: Long)

@Serializable
data class CreateGroupRequest(
    val courseId: String,
    val name: String,
    val callLink: String? = null,
)

@Serializable
data class UpdateGroupRequest(
    val name: String,
    val callLink: String? = null,
)

@Serializable
data class AddGroupMemberRequest(val studentPublicId: String)

@Serializable
data class CourseAccountResponse(
    val publicId: String,
    val displayName: String,
    val roles: List<String>,
)

@Serializable
data class CourseResponse(
    val id: String,
    val teacher: CourseAccountResponse,
    val title: String,
    val subject: String,
    val format: String,
    val callLink: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class EnrollmentResponse(
    val courseId: String,
    val student: CourseAccountResponse,
    val priceMinor: Long,
    val status: String,
    val enrolledAt: String,
    val endedAt: String?,
)

@Serializable
data class GroupResponse(
    val id: String,
    val courseId: String,
    val name: String,
    val callLink: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class GroupMemberResponse(
    val membershipId: String,
    val student: CourseAccountResponse,
    val joinedAt: String,
)

@Serializable
data class CourseErrorResponse(val code: String)

internal fun CreateCourseRequest.toCommand() = CreateCourseCommand(
    title, subject, CourseFormat.valueOf(format.name), callLink,
)

internal fun UpdateCourseRequest.toCommand() = UpdateCourseCommand(title, subject, callLink)
internal fun AddEnrollmentRequest.toCommand() = AddEnrollmentCommand(studentPublicId, priceMinor)
internal fun UpdateGroupRequest.toCommand() = UpdateGroupCommand(name, callLink)

internal fun CourseView.toResponse() = CourseResponse(
    id.toString(), teacher.toResponse(), title, subject, format.name, callLink,
    createdAt.toString(), updatedAt.toString(),
)

internal fun EnrollmentView.toResponse() = EnrollmentResponse(
    courseId.toString(), student.toResponse(), priceMinor, status.name,
    enrolledAt.toString(), endedAt?.toString(),
)

internal fun GroupView.toResponse() = GroupResponse(
    id.toString(), courseId.toString(), name, callLink, createdAt.toString(), updatedAt.toString(),
)

internal fun GroupMemberView.toResponse() = GroupMemberResponse(
    membershipId.toString(), student.toResponse(), joinedAt.toString(),
)

private fun AccountReference.toResponse() = CourseAccountResponse(
    publicId, displayName, roles.map { it.name }.sorted(),
)
