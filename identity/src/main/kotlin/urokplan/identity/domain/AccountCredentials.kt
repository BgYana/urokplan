package urokplan.identity.domain

data class AccountCredentials(
    val account: Account,
    val passwordHash: String,
)
