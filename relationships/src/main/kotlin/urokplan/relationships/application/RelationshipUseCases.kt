package urokplan.relationships.application

import urokplan.relationships.domain.ConnectionType
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
