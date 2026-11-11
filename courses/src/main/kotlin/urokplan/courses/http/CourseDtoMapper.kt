package urokplan.courses.http

import urokplan.courses.application.AddEnrollmentCommand
import urokplan.courses.application.CourseView
import urokplan.courses.application.CreateCourseCommand
import urokplan.courses.application.EnrollmentView
import urokplan.courses.application.GroupMemberView
import urokplan.courses.application.GroupView
import urokplan.courses.application.UpdateCourseCommand
import urokplan.courses.application.UpdateGroupCommand
import urokplan.courses.domain.CourseFormat
import urokplan.identity.application.AccountReference

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
