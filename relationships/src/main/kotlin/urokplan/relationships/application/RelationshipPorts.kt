package urokplan.relationships.application

import urokplan.relationships.domain.Connection
import urokplan.relationships.domain.ConnectionRequest
import urokplan.relationships.domain.ConnectionRequestStatus
import urokplan.relationships.domain.ConnectionType
import java.time.Instant
import java.util.UUID

interface RelationshipRepository {
    fun findPendingRequest(type: ConnectionType, ownerId: UUID, studentId: UUID): ConnectionRequest?
    fun findRequestForUpdate(id: UUID, type: ConnectionType): ConnectionRequest?
    fun createRequest(request: ConnectionRequest): ConnectionRequest
    fun listRequests(accountId: UUID, type: ConnectionType): List<ConnectionRequest>
    fun decideRequest(id: UUID, status: ConnectionRequestStatus, actorId: UUID, decidedAt: Instant)

    fun findConnection(type: ConnectionType, ownerId: UUID, studentId: UUID): Connection?
    fun findConnectionForUpdate(id: UUID, type: ConnectionType): Connection?
    fun activateConnection(connection: Connection): Connection
    fun listConnections(accountId: UUID, type: ConnectionType): List<Connection>
    fun terminateConnection(id: UUID, actorId: UUID, terminatedAt: Instant)
}

class DuplicatePendingRequestException : RuntimeException()
