package urokplan.identity

import urokplan.database.TransactionRunner
import urokplan.identity.application.IdentityApi
import urokplan.identity.application.IdentityAuthentication
import urokplan.identity.application.IdentityService
import urokplan.identity.application.IdentityUseCases
import urokplan.identity.infrastructure.Argon2PasswordHasher
import urokplan.identity.infrastructure.ExposedAccountRepository
import urokplan.identity.infrastructure.ExposedSessionRepository
import urokplan.identity.infrastructure.SecureSessionTokenGenerator
import urokplan.identity.infrastructure.Sha256SessionTokenHasher
import java.time.Clock
import java.time.Duration

class IdentityModule private constructor(
    val useCases: IdentityUseCases,
    val api: IdentityApi,
    val authentication: IdentityAuthentication,
) {
    companion object {
        fun create(
            transactions: TransactionRunner,
            clock: Clock,
        ): IdentityModule {
            val service = IdentityService(
                transactions = transactions,
                accounts = ExposedAccountRepository(),
                sessions = ExposedSessionRepository(),
                passwordHasher = Argon2PasswordHasher(),
                tokenGenerator = SecureSessionTokenGenerator(),
                tokenHasher = Sha256SessionTokenHasher(),
                clock = clock,
                sessionLifetime = Duration.ofDays(30),
            )
            return IdentityModule(useCases = service, api = service, authentication = service)
        }
    }
}
