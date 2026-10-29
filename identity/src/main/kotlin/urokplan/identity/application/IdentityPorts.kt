package urokplan.identity.application

import urokplan.identity.domain.Account
import urokplan.identity.domain.AccountCredentials
import urokplan.identity.domain.NewAccount
import urokplan.identity.domain.Session
import java.time.Instant
import java.util.UUID

interface AccountRepository {
    fun findCredentialsByLogin(login: String): AccountCredentials?
    fun findById(id: UUID): Account?
    fun findByIds(ids: Set<UUID>): List<Account>
    fun findByPublicId(publicId: String): Account?
    fun create(account: NewAccount): Account
    fun updateBillingTimeZone(accountId: UUID, billingTimeZone: String): Account
}

interface SessionRepository {
    fun create(session: Session)
    fun findValidByTokenHash(tokenHash: String, now: Instant): Session?
    fun revoke(sessionId: UUID, revokedAt: Instant)
}

interface PasswordHasher {
    fun hash(password: String): String
    fun verify(password: String, hash: String): Boolean
}

interface SessionTokenGenerator {
    fun generate(): String
}

interface SessionTokenHasher {
    fun hash(token: String): String
}

class DuplicateAccountException : RuntimeException()
