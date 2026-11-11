package urokplan.relationships.http

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionRequestResponse(
    val id: String,
    val type: String,
    val status: String,
    val requester: RelationshipAccountResponse,
    val owner: RelationshipAccountResponse,
    val student: RelationshipAccountResponse,
    val createdAt: String,
    val decidedAt: String?,
)
