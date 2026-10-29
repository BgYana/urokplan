package urokplan.relationships.http

import kotlinx.serialization.Serializable
import urokplan.identity.application.AccountReference
import urokplan.relationships.application.ConnectionView
import urokplan.relationships.application.GuardianActingRole
import urokplan.relationships.application.LearningActingRole
import urokplan.relationships.application.RequestView

@Serializable
enum class LearningActingRoleDto {
    TEACHER,
    STUDENT,
}

@Serializable
enum class GuardianActingRoleDto {
    PARENT,
    STUDENT,
}

@Serializable
data class CreateLearningRequest(
    val targetPublicId: String,
    val actingAs: LearningActingRoleDto,
)

@Serializable
data class CreateGuardianRequest(
    val targetPublicId: String,
    val actingAs: GuardianActingRoleDto,
)

@Serializable
data class RelationshipAccountResponse(
    val publicId: String,
    val displayName: String,
    val roles: List<String>,
)

@Serializable
data class ConnectionRequestResponse(
    val id: String,
    val type: String,
    val status: String,
    val requester: RelationshipAccountResponse,
    val owner: RelationshipAccountResponse,
    val student: RelationshipAccountResponse,
    val createdAt: String,
    val decidedAt: String?,
)

@Serializable
data class ConnectionResponse(
    val id: String,
    val type: String,
    val status: String,
    val owner: RelationshipAccountResponse,
    val student: RelationshipAccountResponse,
    val createdAt: String,
    val terminatedAt: String?,
)

@Serializable
data class RelationshipErrorResponse(val code: String)

internal fun CreateLearningRequest.toActingRole() = LearningActingRole.valueOf(actingAs.name)
internal fun CreateGuardianRequest.toActingRole() = GuardianActingRole.valueOf(actingAs.name)

internal fun RequestView.toResponse() = ConnectionRequestResponse(
    id = id.toString(),
    type = type.name,
    status = status.name,
    requester = requester.toResponse(),
    owner = owner.toResponse(),
    student = student.toResponse(),
    createdAt = createdAt.toString(),
    decidedAt = decidedAt?.toString(),
)

internal fun ConnectionView.toResponse() = ConnectionResponse(
    id = id.toString(),
    type = type.name,
    status = status.name,
    owner = owner.toResponse(),
    student = student.toResponse(),
    createdAt = createdAt.toString(),
    terminatedAt = terminatedAt?.toString(),
)

private fun AccountReference.toResponse() = RelationshipAccountResponse(
    publicId = publicId,
    displayName = displayName,
    roles = roles.map { it.name }.sorted(),
)
