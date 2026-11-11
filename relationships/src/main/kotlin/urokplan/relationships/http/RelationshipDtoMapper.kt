package urokplan.relationships.http

import urokplan.identity.application.AccountReference
import urokplan.relationships.application.ConnectionView
import urokplan.relationships.application.GuardianActingRole
import urokplan.relationships.application.LearningActingRole
import urokplan.relationships.application.RequestView

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
