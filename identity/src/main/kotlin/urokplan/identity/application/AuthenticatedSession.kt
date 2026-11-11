package urokplan.identity.application

import urokplan.identity.domain.Account
import java.util.UUID

data class AuthenticatedSession(
    val sessionId: UUID,
    val account: Account,
)
