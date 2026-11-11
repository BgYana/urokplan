package urokplan.relationships.http

import kotlinx.serialization.Serializable

@Serializable
data class RelationshipAccountResponse(
    val publicId: String,
    val displayName: String,
    val roles: List<String>,
)
