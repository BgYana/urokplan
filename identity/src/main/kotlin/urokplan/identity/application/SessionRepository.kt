package urokplan.identity.application

import urokplan.identity.domain.Session
import java.time.Instant
import java.util.UUID

interface SessionRepository {
    fun create(session: Session)
    fun findValidByTokenHash(tokenHash: String, now: Instant): Session?
    fun revoke(sessionId: UUID, revokedAt: Instant)
}
