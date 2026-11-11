package urokplan.relationships.application

import urokplan.database.TransactionRunner
import urokplan.identity.application.AccountReference
import urokplan.identity.application.IdentityApi
import urokplan.identity.domain.AccountRole
import urokplan.relationships.domain.Connection
import urokplan.relationships.domain.ConnectionRequest
import urokplan.relationships.domain.ConnectionRequestStatus
import urokplan.relationships.domain.ConnectionStatus
import urokplan.relationships.domain.ConnectionType
import java.time.Clock
import java.time.Instant
import java.util.UUID

class RelationshipService(
    private val transactions: TransactionRunner,
    private val repository: RelationshipRepository,
    private val identity: IdentityApi,
    private val clock: Clock,
) : RelationshipUseCases, RelationshipsApi {
    override fun hasActiveLearningConnection(teacherId: UUID, studentId: UUID): Boolean =
        transactions.inTransaction {
            repository.findConnection(ConnectionType.LEARNING, teacherId, studentId) != null
        }

    override fun findActiveGuardianStudentIds(parentId: UUID): Set<UUID> =
        transactions.inTransaction {
            repository.listConnections(parentId, ConnectionType.GUARDIAN)
                .asSequence()
                .filter { it.ownerAccountId == parentId }
                .mapTo(mutableSetOf()) { it.studentAccountId }
        }

    override fun createLearningRequest(
        actorId: UUID,
        targetPublicId: String,
        actingAs: LearningActingRole,
    ): RequestView {
        val actor = requireAccount(actorId)
        val target = requireTarget(targetPublicId)
        val (owner, student) = when (actingAs) {
            LearningActingRole.TEACHER -> {
                actor.requireRole(AccountRole.TEACHER, "actor_is_not_teacher")
                target.requireRole(AccountRole.STUDENT, "target_is_not_student")
                actor to target
            }
            LearningActingRole.STUDENT -> {
                actor.requireRole(AccountRole.STUDENT, "actor_is_not_student")
                target.requireRole(AccountRole.TEACHER, "target_is_not_teacher")
                target to actor
            }
        }
        return createRequest(ConnectionType.LEARNING, actor, owner, student)
    }

    override fun createGuardianRequest(
        actorId: UUID,
        targetPublicId: String,
        actingAs: GuardianActingRole,
    ): RequestView {
        val actor = requireAccount(actorId)
        val target = requireTarget(targetPublicId)
        val (owner, student) = when (actingAs) {
            GuardianActingRole.PARENT -> {
                actor.requireRole(AccountRole.PARENT, "actor_is_not_parent")
                target.requireRole(AccountRole.STUDENT, "target_is_not_student")
                actor to target
            }
            GuardianActingRole.STUDENT -> {
                actor.requireRole(AccountRole.STUDENT, "actor_is_not_student")
                target.requireRole(AccountRole.PARENT, "target_is_not_parent")
                target to actor
            }
        }
        return createRequest(ConnectionType.GUARDIAN, actor, owner, student)
    }

    override fun listRequests(actorId: UUID, type: ConnectionType): List<RequestView> =
        transactions.inTransaction { repository.listRequests(actorId, type) }.toRequestViews()

    override fun acceptRequest(actorId: UUID, requestId: UUID, type: ConnectionType): ConnectionView {
        val connection = transactions.inTransaction {
            val request = requireActionableRequest(actorId, requestId, type)
            if (repository.findConnection(type, request.ownerAccountId, request.studentAccountId) != null) {
                throw RelationshipException("connection_already_active", RelationshipErrorKind.CONFLICT)
            }
            val now = clock.instant()
            repository.decideRequest(request.id, ConnectionRequestStatus.ACCEPTED, actorId, now)
            repository.activateConnection(
                Connection(
                    id = UUID.randomUUID(),
                    type = type,
                    ownerAccountId = request.ownerAccountId,
                    studentAccountId = request.studentAccountId,
                    status = ConnectionStatus.ACTIVE,
                    createdAt = now,
                ),
            )
        }
        return connection.toView(loadReferences(setOf(connection.ownerAccountId, connection.studentAccountId)))
    }

    override fun rejectRequest(actorId: UUID, requestId: UUID, type: ConnectionType): RequestView {
        val rejected = transactions.inTransaction {
            val request = requireActionableRequest(actorId, requestId, type)
            val now = clock.instant()
            repository.decideRequest(request.id, ConnectionRequestStatus.REJECTED, actorId, now)
            request.copy(status = ConnectionRequestStatus.REJECTED, decidedAt = now, decidedBy = actorId)
        }
        return rejected.toView(loadReferences(rejected.accountIds()))
    }

    override fun listConnections(actorId: UUID, type: ConnectionType): List<ConnectionView> {
        val connections = transactions.inTransaction { repository.listConnections(actorId, type) }
        val references = loadReferences(connections.flatMapTo(mutableSetOf()) { it.accountIds() })
        return connections.map { it.toView(references) }
    }

    override fun terminateConnection(actorId: UUID, connectionId: UUID, type: ConnectionType): ConnectionView {
        val terminated = transactions.inTransaction {
            val connection = repository.findConnectionForUpdate(connectionId, type)
                ?: throw RelationshipException("connection_not_found", RelationshipErrorKind.NOT_FOUND)
            if (actorId != connection.ownerAccountId && actorId != connection.studentAccountId) {
                throw RelationshipException("connection_not_found", RelationshipErrorKind.NOT_FOUND)
            }
            if (connection.status != ConnectionStatus.ACTIVE) {
                throw RelationshipException("connection_not_active", RelationshipErrorKind.CONFLICT)
            }
            val now = clock.instant()
            repository.terminateConnection(connection.id, actorId, now)
            connection.copy(status = ConnectionStatus.TERMINATED, terminatedAt = now, terminatedBy = actorId)
        }
        return terminated.toView(loadReferences(terminated.accountIds()))
    }

    private fun createRequest(
        type: ConnectionType,
        actor: AccountReference,
        owner: AccountReference,
        student: AccountReference,
    ): RequestView {
        if (owner.id == student.id) {
            throw RelationshipException("self_connection_forbidden", RelationshipErrorKind.BAD_REQUEST)
        }
        val request = try {
            transactions.inTransaction {
                if (repository.findConnection(type, owner.id, student.id) != null) {
                    throw RelationshipException("connection_already_active", RelationshipErrorKind.CONFLICT)
                }
                if (repository.findPendingRequest(type, owner.id, student.id) != null) {
                    throw RelationshipException("request_already_pending", RelationshipErrorKind.CONFLICT)
                }
                repository.createRequest(
                    ConnectionRequest(
                        id = UUID.randomUUID(),
                        type = type,
                        ownerAccountId = owner.id,
                        studentAccountId = student.id,
                        requesterAccountId = actor.id,
                        status = ConnectionRequestStatus.PENDING,
                        createdAt = clock.instant(),
                    ),
                )
            }
        } catch (_: DuplicatePendingRequestException) {
            throw RelationshipException("request_already_pending", RelationshipErrorKind.CONFLICT)
        }
        return request.toView(mapOf(owner.id to owner, student.id to student))
    }

    private fun requireActionableRequest(actorId: UUID, requestId: UUID, type: ConnectionType): ConnectionRequest {
        val request = repository.findRequestForUpdate(requestId, type)
            ?: throw RelationshipException("request_not_found", RelationshipErrorKind.NOT_FOUND)
        if (actorId != request.ownerAccountId && actorId != request.studentAccountId) {
            throw RelationshipException("request_not_found", RelationshipErrorKind.NOT_FOUND)
        }
        if (actorId != request.recipientAccountId()) {
            throw RelationshipException("only_recipient_can_decide", RelationshipErrorKind.FORBIDDEN)
        }
        if (request.status != ConnectionRequestStatus.PENDING) {
            throw RelationshipException("request_already_decided", RelationshipErrorKind.CONFLICT)
        }
        return request
    }

    private fun requireAccount(id: UUID): AccountReference =
        identity.findAccountsByIds(setOf(id))[id]
            ?: throw RelationshipException("account_not_found", RelationshipErrorKind.NOT_FOUND)

    private fun requireTarget(publicId: String): AccountReference =
        identity.findAccountByPublicId(publicId)
            ?: throw RelationshipException("account_not_found", RelationshipErrorKind.NOT_FOUND)

    private fun loadReferences(ids: Set<UUID>): Map<UUID, AccountReference> {
        val references = identity.findAccountsByIds(ids)
        if (references.size != ids.size) {
            throw IllegalStateException("Relationship references a missing account")
        }
        return references
    }

    private fun List<ConnectionRequest>.toRequestViews(): List<RequestView> {
        val references = loadReferences(flatMapTo(mutableSetOf()) { it.accountIds() })
        return map { it.toView(references) }
    }

    private fun ConnectionRequest.accountIds() = setOf(ownerAccountId, studentAccountId, requesterAccountId)
    private fun Connection.accountIds() = setOf(ownerAccountId, studentAccountId)

    private fun ConnectionRequest.toView(references: Map<UUID, AccountReference>) = RequestView(
        id = id,
        type = type,
        status = status,
        requester = references.getValue(requesterAccountId),
        owner = references.getValue(ownerAccountId),
        student = references.getValue(studentAccountId),
        createdAt = createdAt,
        decidedAt = decidedAt,
    )

    private fun Connection.toView(references: Map<UUID, AccountReference>) = ConnectionView(
        id = id,
        type = type,
        status = status,
        owner = references.getValue(ownerAccountId),
        student = references.getValue(studentAccountId),
        createdAt = createdAt,
        terminatedAt = terminatedAt,
    )
}
