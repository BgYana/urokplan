package urokplan.relationships.application

import urokplan.identity.application.AccountReference
import urokplan.identity.domain.AccountRole
import urokplan.relationships.domain.ConnectionRequestStatus
import urokplan.relationships.domain.ConnectionStatus
import urokplan.relationships.domain.ConnectionType
import java.time.Instant
import java.util.UUID

enum class LearningActingRole {
    TEACHER,
    STUDENT,
}

enum class GuardianActingRole {
    PARENT,
    STUDENT,
}

interface RelationshipUseCases {
    fun createLearningRequest(actorId: UUID, targetPublicId: String, actingAs: LearningActingRole): RequestView
    fun createGuardianRequest(actorId: UUID, targetPublicId: String, actingAs: GuardianActingRole): RequestView
    fun listRequests(actorId: UUID, type: ConnectionType): List<RequestView>
    fun acceptRequest(actorId: UUID, requestId: UUID, type: ConnectionType): ConnectionView
    fun rejectRequest(actorId: UUID, requestId: UUID, type: ConnectionType): RequestView
    fun listConnections(actorId: UUID, type: ConnectionType): List<ConnectionView>
    fun terminateConnection(actorId: UUID, connectionId: UUID, type: ConnectionType): ConnectionView
}

interface RelationshipsApi {
    fun hasActiveLearningConnection(teacherId: UUID, studentId: UUID): Boolean
    fun findActiveGuardianStudentIds(parentId: UUID): Set<UUID>
}

data class RequestView(
    val id: UUID,
    val type: ConnectionType,
    val status: ConnectionRequestStatus,
    val requester: AccountReference,
    val owner: AccountReference,
    val student: AccountReference,
    val createdAt: Instant,
    val decidedAt: Instant?,
)

data class ConnectionView(
    val id: UUID,
    val type: ConnectionType,
    val status: ConnectionStatus,
    val owner: AccountReference,
    val student: AccountReference,
    val createdAt: Instant,
    val terminatedAt: Instant?,
)

enum class RelationshipErrorKind {
    BAD_REQUEST,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
}

class RelationshipException(
    val errorCode: String,
    val kind: RelationshipErrorKind,
) : RuntimeException(errorCode)

internal fun AccountReference.requireRole(role: AccountRole, code: String) {
    if (role !in roles) throw RelationshipException(code, RelationshipErrorKind.BAD_REQUEST)
}
