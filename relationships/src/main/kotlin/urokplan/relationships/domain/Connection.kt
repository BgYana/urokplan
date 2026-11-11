package urokplan.relationships.domain

import java.time.Instant
import java.util.UUID

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
