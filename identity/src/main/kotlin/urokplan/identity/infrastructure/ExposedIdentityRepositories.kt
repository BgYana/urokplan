package urokplan.identity.infrastructure

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import urokplan.identity.application.AccountRepository
import urokplan.identity.application.DuplicateAccountException
import urokplan.identity.application.SessionRepository
import urokplan.identity.domain.Account
import urokplan.identity.domain.AccountCredentials
import urokplan.identity.domain.AccountRole
import urokplan.identity.domain.NewAccount
import urokplan.identity.domain.Session
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

internal class ExposedAccountRepository : AccountRepository {
    override fun findCredentialsByLogin(login: String): AccountCredentials? {
        val row = AccountsTable.selectAll().where { AccountsTable.login eq login }.singleOrNull()
            ?: return null
        return AccountCredentials(row.toAccount(loadRoles(row[AccountsTable.id])), row[AccountsTable.passwordHash])
    }

    override fun findById(id: UUID): Account? {
        val row = AccountsTable.selectAll().where { AccountsTable.id eq id }.singleOrNull()
            ?: return null
        return row.toAccount(loadRoles(id))
    }

    override fun findByIds(ids: Set<UUID>): List<Account> {
        if (ids.isEmpty()) return emptyList()
        val rows = AccountsTable.selectAll().where { AccountsTable.id inList ids }.toList()
        val rolesByAccount = AccountRolesTable.selectAll()
            .where { AccountRolesTable.accountId inList ids }
            .groupBy({ it[AccountRolesTable.accountId] }, { it[AccountRolesTable.role] })
        return rows.map { row -> row.toAccount(rolesByAccount[row[AccountsTable.id]].orEmpty().toSet()) }
    }

    override fun findByPublicId(publicId: String): Account? {
        val row = AccountsTable.selectAll().where { AccountsTable.publicId eq publicId }.singleOrNull()
            ?: return null
        return row.toAccount(loadRoles(row[AccountsTable.id]))
    }

    override fun create(account: NewAccount): Account {
        try {
            AccountsTable.insert {
                it[id] = account.id
                it[publicId] = account.publicId
                it[login] = account.login
                it[displayName] = account.displayName
                it[billingTimeZone] = account.billingTimeZone
                it[passwordHash] = account.passwordHash
                it[createdAt] = account.createdAt
            }
        } catch (exception: ExposedSQLException) {
            if (exception.hasSqlState("23505")) throw DuplicateAccountException()
            throw exception
        }
        account.roles.forEach { accountRole ->
            AccountRolesTable.insert {
                it[accountId] = account.id
                it[role] = accountRole
            }
        }
        return Account(
            account.id,
            account.publicId,
            account.login,
            account.displayName,
            account.roles,
            account.createdAt,
            account.billingTimeZone,
        )
    }

    private fun loadRoles(accountId: UUID): Set<AccountRole> =
        AccountRolesTable.selectAll()
            .where { AccountRolesTable.accountId eq accountId }
            .mapTo(linkedSetOf()) { it[AccountRolesTable.role] }

    private fun ResultRow.toAccount(roles: Set<AccountRole>) = Account(
        id = this[AccountsTable.id],
        publicId = this[AccountsTable.publicId],
        login = this[AccountsTable.login],
        displayName = this[AccountsTable.displayName],
        roles = roles,
        createdAt = this[AccountsTable.createdAt],
        billingTimeZone = this[AccountsTable.billingTimeZone],
    )

    override fun updateBillingTimeZone(accountId: UUID, billingTimeZone: String): Account {
        AccountsTable.update({ AccountsTable.id eq accountId }) {
            it[AccountsTable.billingTimeZone] = billingTimeZone
        }
        return requireNotNull(findById(accountId))
    }

    private fun Throwable.hasSqlState(expected: String): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SQLException && current.sqlState == expected) return true
            current = current.cause
        }
        return false
    }
}

internal class ExposedSessionRepository : SessionRepository {
    override fun create(session: Session) {
        SessionsTable.insert {
            it[id] = session.id
            it[accountId] = session.accountId
            it[tokenHash] = session.tokenHash
            it[createdAt] = session.createdAt
            it[expiresAt] = session.expiresAt
        }
    }

    override fun findValidByTokenHash(tokenHash: String, now: Instant): Session? =
        SessionsTable.selectAll()
            .where {
                (SessionsTable.tokenHash eq tokenHash) and
                    (SessionsTable.revokedAt.isNull()) and
                    (SessionsTable.expiresAt greater now)
            }
            .singleOrNull()
            ?.toSession()

    override fun revoke(sessionId: UUID, revokedAt: Instant) {
        SessionsTable.update({
            (SessionsTable.id eq sessionId) and SessionsTable.revokedAt.isNull()
        }) {
            it[SessionsTable.revokedAt] = revokedAt
        }
    }

    private fun ResultRow.toSession() = Session(
        id = this[SessionsTable.id],
        accountId = this[SessionsTable.accountId],
        tokenHash = this[SessionsTable.tokenHash],
        createdAt = this[SessionsTable.createdAt],
        expiresAt = this[SessionsTable.expiresAt],
    )
}
