package urokplan.relationships.application

import urokplan.identity.application.AccountReference
import urokplan.relationships.domain.ConnectionRequestStatus
import urokplan.relationships.domain.ConnectionType
import java.time.Instant
import java.util.UUID

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
