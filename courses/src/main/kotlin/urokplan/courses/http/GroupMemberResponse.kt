package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class GroupMemberResponse(
    val membershipId: String,
    val student: CourseAccountResponse,
    val joinedAt: String,
)
