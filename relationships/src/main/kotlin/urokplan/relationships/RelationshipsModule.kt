package urokplan.relationships

import urokplan.database.TransactionRunner
import urokplan.identity.application.IdentityApi
import urokplan.relationships.application.RelationshipService
import urokplan.relationships.application.RelationshipUseCases
import urokplan.relationships.application.RelationshipsApi
import urokplan.relationships.infrastructure.ExposedRelationshipRepository
import java.time.Clock

class RelationshipsModule private constructor(
    val useCases: RelationshipUseCases,
    val api: RelationshipsApi,
) {
    companion object {
        fun create(
            transactions: TransactionRunner,
            identity: IdentityApi,
            clock: Clock,
        ): RelationshipsModule {
            val service = RelationshipService(
                transactions = transactions,
                repository = ExposedRelationshipRepository(),
                identity = identity,
                clock = clock,
            )
            return RelationshipsModule(useCases = service, api = service)
        }
    }
}
