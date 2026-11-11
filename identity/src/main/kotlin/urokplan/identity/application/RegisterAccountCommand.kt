package urokplan.identity.application

import urokplan.identity.domain.AccountRole

data class RegisterAccountCommand(
    val login: String,
    val displayName: String,
    val password: String,
    val roles: Set<AccountRole>,
    val billingTimeZone: String = "UTC",
)
