package urokplan.identity.domain

import java.time.Instant
import java.util.UUID

enum class AccountRole {
    STUDENT,
    TEACHER,
    PARENT,
}

data class Account(
    val id: UUID,
    val publicId: String,
    val login: String,
    val displayName: String,
    val roles: Set<AccountRole>,
    val createdAt: Instant,
    val billingTimeZone: String = "UTC",
)

data class AccountCredentials(
    val account: Account,
    val passwordHash: String,
)

data class NewAccount(
    val id: UUID,
    val publicId: String,
    val login: String,
    val displayName: String,
    val passwordHash: String,
    val roles: Set<AccountRole>,
    val createdAt: Instant,
    val billingTimeZone: String = "UTC",
)

data class Session(
    val id: UUID,
    val accountId: UUID,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
)
