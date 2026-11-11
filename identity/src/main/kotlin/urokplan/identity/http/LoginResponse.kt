package urokplan.identity.http

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: String,
    val account: AccountResponse,
)
