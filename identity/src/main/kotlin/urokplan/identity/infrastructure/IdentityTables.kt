package urokplan.identity.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import urokplan.identity.domain.AccountRole

internal object AccountsTable : Table("identity.accounts") {
    val id = uuid("id")
    val publicId = varchar("public_id", 36)
    val login = varchar("login", 50)
    val displayName = varchar("display_name", 100)
    val billingTimeZone = varchar("billing_time_zone", 80)
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

internal object AccountRolesTable : Table("identity.account_roles") {
    val accountId = uuid("account_id")
    val role = enumerationByName<AccountRole>("role", 20)

    override val primaryKey = PrimaryKey(accountId, role)
}

internal object SessionsTable : Table("identity.sessions") {
    val id = uuid("id")
    val accountId = uuid("account_id")
    val tokenHash = varchar("token_hash", 64)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
