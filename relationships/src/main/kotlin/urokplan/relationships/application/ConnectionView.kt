package urokplan.relationships.application

import urokplan.identity.application.AccountReference
import urokplan.relationships.domain.ConnectionStatus
import urokplan.relationships.domain.ConnectionType
import java.time.Instant
import java.util.UUID

data class ConnectionView(
    val id: UUID,
    val type: ConnectionType,
    val status: ConnectionStatus,
    val owner: AccountReference,
    val student: AccountReference,
    val createdAt: Instant,
    val terminatedAt: Instant?,
)
