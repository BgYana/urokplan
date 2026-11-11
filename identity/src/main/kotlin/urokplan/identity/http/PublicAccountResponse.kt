package urokplan.identity.http

import kotlinx.serialization.Serializable

@Serializable
data class PublicAccountResponse(
    val publicId: String,
    val displayName: String,
    val roles: List<AccountRoleDto>,
)
