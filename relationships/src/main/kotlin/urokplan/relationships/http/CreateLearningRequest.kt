package urokplan.relationships.http

import kotlinx.serialization.Serializable

@Serializable
data class CreateLearningRequest(
    val targetPublicId: String,
    val actingAs: LearningActingRoleDto,
)
