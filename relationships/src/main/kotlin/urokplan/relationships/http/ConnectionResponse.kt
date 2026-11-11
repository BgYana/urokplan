package urokplan.relationships.http

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionResponse(
    val id: String,
    val type: String,
    val status: String,
    val owner: RelationshipAccountResponse,
    val student: RelationshipAccountResponse,
    val createdAt: String,
    val terminatedAt: String?,
)
