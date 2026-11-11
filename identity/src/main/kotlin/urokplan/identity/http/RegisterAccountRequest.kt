package urokplan.identity.http

import kotlinx.serialization.Serializable

@Serializable
data class RegisterAccountRequest(
    val login: String,
    val displayName: String,
    val password: String,
    val roles: Set<AccountRoleDto>,
    val billingTimeZone: String = "UTC",
)
