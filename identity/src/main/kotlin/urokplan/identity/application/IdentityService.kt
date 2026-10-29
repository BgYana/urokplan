package urokplan.identity.application

import urokplan.database.TransactionRunner
import urokplan.identity.domain.Account
import urokplan.identity.domain.NewAccount
import urokplan.identity.domain.Session
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.UUID

internal class IdentityService(
    private val transactions: TransactionRunner,
    private val accounts: AccountRepository,
    private val sessions: SessionRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenGenerator: SessionTokenGenerator,
    private val tokenHasher: SessionTokenHasher,
    private val clock: Clock,
    private val sessionLifetime: Duration,
    private val idGenerator: () -> UUID = UUID::randomUUID,
) : IdentityUseCases, IdentityApi, IdentityAuthentication {
    override fun register(command: RegisterAccountCommand): Account {
        validateRegistration(command)
        val account = NewAccount(
            id = idGenerator(),
            publicId = idGenerator().toString(),
            login = command.login,
            displayName = command.displayName.trim(),
            passwordHash = passwordHasher.hash(command.password),
            roles = command.roles,
            createdAt = clock.instant(),
            billingTimeZone = command.billingTimeZone,
        )

        return try {
            transactions.inTransaction {
                if (accounts.findCredentialsByLogin(command.login) != null) {
                    throw LoginAlreadyExistsException()
                }
                accounts.create(account)
            }
        } catch (_: DuplicateAccountException) {
            throw LoginAlreadyExistsException()
        }
    }

    override fun login(command: LoginCommand): LoginResult {
        val credentials = transactions.inTransaction {
            accounts.findCredentialsByLogin(command.login)
        } ?: throw InvalidCredentialsException()

        if (!passwordHasher.verify(command.password, credentials.passwordHash)) {
            throw InvalidCredentialsException()
        }

        val now = clock.instant()
        val token = tokenGenerator.generate()
        val session = Session(
            id = idGenerator(),
            accountId = credentials.account.id,
            tokenHash = tokenHasher.hash(token),
            createdAt = now,
            expiresAt = now.plus(sessionLifetime),
        )
        transactions.inTransaction {
            sessions.create(session)
        }

        return LoginResult(token, session.expiresAt, credentials.account)
    }

    override fun resolveSession(token: String): AuthenticatedSession? {
        if (token.isBlank()) return null
        return transactions.inTransaction {
            val session = sessions.findValidByTokenHash(tokenHasher.hash(token), clock.instant())
                ?: return@inTransaction null
            val account = accounts.findById(session.accountId) ?: return@inTransaction null
            AuthenticatedSession(session.id, account)
        }
    }

    override fun logout(sessionId: UUID) {
        transactions.inTransaction {
            sessions.revoke(sessionId, clock.instant())
        }
    }

    override fun updateBillingTimeZone(accountId: UUID, billingTimeZone: String): Account {
        validateBillingTimeZone(billingTimeZone)
        return transactions.inTransaction {
            accounts.findById(accountId) ?: throw RegistrationValidationException("account_not_found")
            accounts.updateBillingTimeZone(accountId, billingTimeZone)
        }
    }

    override fun findPublicProfile(publicId: String): PublicAccountProfile? =
        transactions.inTransaction {
            accounts.findByPublicId(publicId)?.let { account ->
                PublicAccountProfile(account.publicId, account.displayName, account.roles)
            }
        }

    override fun findAccountByPublicId(publicId: String): AccountReference? =
        transactions.inTransaction {
            accounts.findByPublicId(publicId)?.toReference()
        }

    override fun findAccountsByIds(ids: Set<UUID>): Map<UUID, AccountReference> {
        if (ids.isEmpty()) return emptyMap()
        return transactions.inTransaction {
            accounts.findByIds(ids).associate { account -> account.id to account.toReference() }
        }
    }

    private fun Account.toReference() = AccountReference(id, publicId, displayName, roles, billingTimeZone)

    private fun validateRegistration(command: RegisterAccountCommand) {
        if (!LOGIN_PATTERN.matches(command.login)) {
            throw RegistrationValidationException("invalid_login")
        }
        val displayName = command.displayName.trim()
        if (displayName.length !in DISPLAY_NAME_LENGTH || displayName.any(Char::isISOControl)) {
            throw RegistrationValidationException("invalid_display_name")
        }
        if (command.password.length !in PASSWORD_LENGTH) {
            throw RegistrationValidationException("invalid_password")
        }
        if (command.roles.isEmpty()) {
            throw RegistrationValidationException("roles_required")
        }
        validateBillingTimeZone(command.billingTimeZone)
    }

    private fun validateBillingTimeZone(value: String) {
        if (runCatching { ZoneId.of(value) }.isFailure) {
            throw RegistrationValidationException("invalid_billing_time_zone")
        }
    }

    private companion object {
        val LOGIN_PATTERN = Regex("[A-Za-z0-9._-]{3,50}")
        val DISPLAY_NAME_LENGTH = 2..100
        val PASSWORD_LENGTH = 8..128
    }
}
