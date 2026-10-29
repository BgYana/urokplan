package urokplan.identity.application

import urokplan.identity.domain.Account
import urokplan.identity.domain.AccountRole
import java.time.Instant
import java.util.UUID

interface IdentityApi {
    fun findAccountByPublicId(publicId: String): AccountReference?
    fun findAccountsByIds(ids: Set<UUID>): Map<UUID, AccountReference>
}

interface IdentityAuthentication {
    fun resolveSession(token: String): AuthenticatedSession?
}

interface IdentityUseCases {
    fun register(command: RegisterAccountCommand): Account
    fun login(command: LoginCommand): LoginResult
    fun logout(sessionId: UUID)
    fun findPublicProfile(publicId: String): PublicAccountProfile?
    fun updateBillingTimeZone(accountId: UUID, billingTimeZone: String): Account
}

data class RegisterAccountCommand(
    val login: String,
    val displayName: String,
    val password: String,
    val roles: Set<AccountRole>,
    val billingTimeZone: String = "UTC",
)

data class LoginCommand(
    val login: String,
    val password: String,
)

data class LoginResult(
    val accessToken: String,
    val expiresAt: Instant,
    val account: Account,
)

data class AuthenticatedSession(
    val sessionId: UUID,
    val account: Account,
)

data class PublicAccountProfile(
    val publicId: String,
    val displayName: String,
    val roles: Set<AccountRole>,
)

data class AccountReference(
    val id: UUID,
    val publicId: String,
    val displayName: String,
    val roles: Set<AccountRole>,
    val billingTimeZone: String = "UTC",
)

class RegistrationValidationException(val errorCode: String) : RuntimeException(errorCode)

class LoginAlreadyExistsException : RuntimeException("login_already_exists")

class InvalidCredentialsException : RuntimeException("invalid_credentials")
