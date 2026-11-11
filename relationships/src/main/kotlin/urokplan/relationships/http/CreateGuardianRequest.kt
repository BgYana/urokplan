package urokplan.relationships.http

import kotlinx.serialization.Serializable

@Serializable
data class CreateGuardianRequest(
    val targetPublicId: String,
    val actingAs: GuardianActingRoleDto,
)
