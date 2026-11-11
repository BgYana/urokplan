package urokplan.identity.http

import kotlinx.serialization.Serializable

@Serializable
data class AccountResponse(
    val publicId: String,
    val login: String,
    val displayName: String,
    val roles: List<AccountRoleDto>,
    val billingTimeZone: String,
)
