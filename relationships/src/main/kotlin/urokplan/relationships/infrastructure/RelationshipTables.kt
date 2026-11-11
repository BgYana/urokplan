package urokplan.relationships.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import urokplan.relationships.domain.ConnectionRequestStatus
import urokplan.relationships.domain.ConnectionStatus
import urokplan.relationships.domain.ConnectionType

internal object ConnectionRequestsTable : Table("relationships.connection_requests") {
    val id = uuid("id")
    val type = enumerationByName<ConnectionType>("type", 20)
    val ownerAccountId = uuid("owner_account_id")
    val studentAccountId = uuid("student_account_id")
    val requesterAccountId = uuid("requester_account_id")
    val status = enumerationByName<ConnectionRequestStatus>("status", 20)
    val createdAt = timestamp("created_at")
    val decidedAt = timestamp("decided_at").nullable()
    val decidedBy = uuid("decided_by").nullable()

    override val primaryKey = PrimaryKey(id)
}

internal object ConnectionsTable : Table("relationships.connections") {
    val id = uuid("id")
    val type = enumerationByName<ConnectionType>("type", 20)
    val ownerAccountId = uuid("owner_account_id")
    val studentAccountId = uuid("student_account_id")
    val status = enumerationByName<ConnectionStatus>("status", 20)
    val createdAt = timestamp("created_at")
    val terminatedAt = timestamp("terminated_at").nullable()
    val terminatedBy = uuid("terminated_by").nullable()

    override val primaryKey = PrimaryKey(id)
}
