package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class AddGroupMemberRequest(val studentPublicId: String)
