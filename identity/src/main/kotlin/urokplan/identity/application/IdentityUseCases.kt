package urokplan.identity.application

import urokplan.identity.domain.Account
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

class RegistrationValidationException(val errorCode: String) : RuntimeException(errorCode)

class LoginAlreadyExistsException : RuntimeException("login_already_exists")

class InvalidCredentialsException : RuntimeException("invalid_credentials")
