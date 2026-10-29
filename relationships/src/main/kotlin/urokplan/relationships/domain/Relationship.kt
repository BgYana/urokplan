package urokplan.relationships.domain

import java.time.Instant
import java.util.UUID

enum class ConnectionType {
    LEARNING,
    GUARDIAN,
}

enum class ConnectionRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
}

enum class ConnectionStatus {
    ACTIVE,
    TERMINATED,
}

data class ConnectionRequest(
    val id: UUID,
    val type: ConnectionType,
    val ownerAccountId: UUID,
    val studentAccountId: UUID,
    val requesterAccountId: UUID,
    val status: ConnectionRequestStatus,
    val createdAt: Instant,
    val decidedAt: Instant? = null,
    val decidedBy: UUID? = null,
) {
    fun recipientAccountId(): UUID =
        if (requesterAccountId == ownerAccountId) studentAccountId else ownerAccountId
}

data class Connection(
    val id: UUID,
    val type: ConnectionType,
    val ownerAccountId: UUID,
    val studentAccountId: UUID,
    val status: ConnectionStatus,
    val createdAt: Instant,
    val terminatedAt: Instant? = null,
    val terminatedBy: UUID? = null,
)
