package urokplan.identity.http

import kotlinx.serialization.Serializable

@Serializable
data class IdentityErrorResponse(val code: String)
