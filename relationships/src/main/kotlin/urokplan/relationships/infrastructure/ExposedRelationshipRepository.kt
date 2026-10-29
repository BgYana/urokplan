package urokplan.relationships.infrastructure

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import urokplan.relationships.application.DuplicatePendingRequestException
import urokplan.relationships.application.RelationshipRepository
import urokplan.relationships.domain.Connection
import urokplan.relationships.domain.ConnectionRequest
import urokplan.relationships.domain.ConnectionRequestStatus
import urokplan.relationships.domain.ConnectionStatus
import urokplan.relationships.domain.ConnectionType
import java.time.Instant
import java.util.UUID

class ExposedRelationshipRepository : RelationshipRepository {
    override fun findPendingRequest(
        type: ConnectionType,
        ownerId: UUID,
        studentId: UUID,
    ): ConnectionRequest? = ConnectionRequestsTable.selectAll().where {
        (ConnectionRequestsTable.type eq type) and
            (ConnectionRequestsTable.ownerAccountId eq ownerId) and
            (ConnectionRequestsTable.studentAccountId eq studentId) and
            (ConnectionRequestsTable.status eq ConnectionRequestStatus.PENDING)
    }.singleOrNull()?.toRequest()

    override fun findRequestForUpdate(id: UUID, type: ConnectionType): ConnectionRequest? =
        ConnectionRequestsTable.selectAll()
            .where { (ConnectionRequestsTable.id eq id) and (ConnectionRequestsTable.type eq type) }
            .forUpdate()
            .singleOrNull()
            ?.toRequest()

    override fun createRequest(request: ConnectionRequest): ConnectionRequest {
        try {
            ConnectionRequestsTable.insert {
                it[id] = request.id
                it[type] = request.type
                it[ownerAccountId] = request.ownerAccountId
                it[studentAccountId] = request.studentAccountId
                it[requesterAccountId] = request.requesterAccountId
                it[status] = request.status
                it[createdAt] = request.createdAt
            }
        } catch (exception: ExposedSQLException) {
            if (exception.sqlState == "23505") throw DuplicatePendingRequestException()
            throw exception
        }
        return request
    }

    override fun listRequests(accountId: UUID, type: ConnectionType): List<ConnectionRequest> =
        ConnectionRequestsTable.selectAll().where {
            (ConnectionRequestsTable.type eq type) and
                ((ConnectionRequestsTable.ownerAccountId eq accountId) or
                    (ConnectionRequestsTable.studentAccountId eq accountId))
        }.orderBy(ConnectionRequestsTable.createdAt).map(ResultRow::toRequest)

    override fun decideRequest(
        id: UUID,
        status: ConnectionRequestStatus,
        actorId: UUID,
        decidedAt: Instant,
    ) {
        ConnectionRequestsTable.update({ ConnectionRequestsTable.id eq id }) {
            it[ConnectionRequestsTable.status] = status
            it[ConnectionRequestsTable.decidedBy] = actorId
            it[ConnectionRequestsTable.decidedAt] = decidedAt
        }
    }

    override fun findConnection(type: ConnectionType, ownerId: UUID, studentId: UUID): Connection? =
        ConnectionsTable.selectAll().where {
            (ConnectionsTable.type eq type) and
                (ConnectionsTable.ownerAccountId eq ownerId) and
                (ConnectionsTable.studentAccountId eq studentId) and
                (ConnectionsTable.status eq ConnectionStatus.ACTIVE)
        }.singleOrNull()?.toConnection()

    override fun findConnectionForUpdate(id: UUID, type: ConnectionType): Connection? =
        ConnectionsTable.selectAll()
            .where { (ConnectionsTable.id eq id) and (ConnectionsTable.type eq type) }
            .forUpdate()
            .singleOrNull()
            ?.toConnection()

    override fun activateConnection(connection: Connection): Connection {
        ConnectionsTable.insert {
            it[id] = connection.id
            it[type] = connection.type
            it[ownerAccountId] = connection.ownerAccountId
            it[studentAccountId] = connection.studentAccountId
            it[status] = connection.status
            it[createdAt] = connection.createdAt
        }
        return connection
    }

    override fun listConnections(accountId: UUID, type: ConnectionType): List<Connection> =
        ConnectionsTable.selectAll().where {
            (ConnectionsTable.type eq type) and
                (ConnectionsTable.status eq ConnectionStatus.ACTIVE) and
                ((ConnectionsTable.ownerAccountId eq accountId) or
                    (ConnectionsTable.studentAccountId eq accountId))
        }.orderBy(ConnectionsTable.createdAt).map(ResultRow::toConnection)

    override fun terminateConnection(id: UUID, actorId: UUID, terminatedAt: Instant) {
        ConnectionsTable.update({ ConnectionsTable.id eq id }) {
            it[status] = ConnectionStatus.TERMINATED
            it[terminatedBy] = actorId
            it[ConnectionsTable.terminatedAt] = terminatedAt
        }
    }
}

private fun ResultRow.toRequest() = ConnectionRequest(
    id = this[ConnectionRequestsTable.id],
    type = this[ConnectionRequestsTable.type],
    ownerAccountId = this[ConnectionRequestsTable.ownerAccountId],
    studentAccountId = this[ConnectionRequestsTable.studentAccountId],
    requesterAccountId = this[ConnectionRequestsTable.requesterAccountId],
    status = this[ConnectionRequestsTable.status],
    createdAt = this[ConnectionRequestsTable.createdAt],
    decidedAt = this[ConnectionRequestsTable.decidedAt],
    decidedBy = this[ConnectionRequestsTable.decidedBy],
)

private fun ResultRow.toConnection() = Connection(
    id = this[ConnectionsTable.id],
    type = this[ConnectionsTable.type],
    ownerAccountId = this[ConnectionsTable.ownerAccountId],
    studentAccountId = this[ConnectionsTable.studentAccountId],
    status = this[ConnectionsTable.status],
    createdAt = this[ConnectionsTable.createdAt],
    terminatedAt = this[ConnectionsTable.terminatedAt],
    terminatedBy = this[ConnectionsTable.terminatedBy],
)
