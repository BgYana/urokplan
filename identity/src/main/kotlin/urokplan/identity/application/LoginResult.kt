package urokplan.identity.application

import urokplan.identity.domain.Account
import java.time.Instant

data class LoginResult(
    val accessToken: String,
    val expiresAt: Instant,
    val account: Account,
)
