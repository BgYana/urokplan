package urokplan.identity.domain

import java.time.Instant
import java.util.UUID

data class Account(
    val id: UUID,
    val publicId: String,
    val login: String,
    val displayName: String,
    val roles: Set<AccountRole>,
    val createdAt: Instant,
    val billingTimeZone: String = "UTC",
)
