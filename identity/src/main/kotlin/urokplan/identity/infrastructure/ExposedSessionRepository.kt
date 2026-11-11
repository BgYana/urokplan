package urokplan.identity.infrastructure

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import urokplan.identity.application.SessionRepository
import urokplan.identity.domain.Session
import java.time.Instant
import java.util.UUID

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
