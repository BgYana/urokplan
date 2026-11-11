package urokplan.identity.domain

import java.time.Instant
import java.util.UUID

data class Session(
    val id: UUID,
    val accountId: UUID,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
)
