package urokplan.relationships.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import urokplan.identity.application.IdentityAuthentication
import urokplan.identity.http.requireAuthenticated
import urokplan.relationships.application.RelationshipErrorKind
import urokplan.relationships.application.RelationshipException
import urokplan.relationships.application.RelationshipUseCases
import urokplan.relationships.domain.ConnectionType
import java.util.UUID

fun Route.relationshipRoutes(
    useCases: RelationshipUseCases,
    authentication: IdentityAuthentication,
) {
    route("/api/learning-connections") {
        post("/requests") {
            val session = call.requireAuthenticated(authentication) ?: return@post
            val request = call.receiveOrReject<CreateLearningRequest>() ?: return@post
            call.executeRelationship(HttpStatusCode.Created) {
                useCases.createLearningRequest(
                    actorId = session.account.id,
                    targetPublicId = request.targetPublicId,
                    actingAs = request.toActingRole(),
                ).toResponse()
            }
        }
        relationshipLifecycleRoutes(useCases, authentication, ConnectionType.LEARNING)
    }

    route("/api/guardian-connections") {
        post("/requests") {
            val session = call.requireAuthenticated(authentication) ?: return@post
            val request = call.receiveOrReject<CreateGuardianRequest>() ?: return@post
            call.executeRelationship(HttpStatusCode.Created) {
                useCases.createGuardianRequest(
                    actorId = session.account.id,
                    targetPublicId = request.targetPublicId,
                    actingAs = request.toActingRole(),
                ).toResponse()
            }
        }
        relationshipLifecycleRoutes(useCases, authentication, ConnectionType.GUARDIAN)
    }
}

private fun Route.relationshipLifecycleRoutes(
    useCases: RelationshipUseCases,
    authentication: IdentityAuthentication,
    type: ConnectionType,
) {
    get("/requests") {
        val session = call.requireAuthenticated(authentication) ?: return@get
        call.executeRelationship {
            useCases.listRequests(session.account.id, type).map { it.toResponse() }
        }
    }
    post("/requests/{requestId}/accept") {
        val session = call.requireAuthenticated(authentication) ?: return@post
        val requestId = call.requireUuid("requestId") ?: return@post
        call.executeRelationship {
            useCases.acceptRequest(session.account.id, requestId, type).toResponse()
        }
    }
    post("/requests/{requestId}/reject") {
        val session = call.requireAuthenticated(authentication) ?: return@post
        val requestId = call.requireUuid("requestId") ?: return@post
        call.executeRelationship {
            useCases.rejectRequest(session.account.id, requestId, type).toResponse()
        }
    }
    get {
        val session = call.requireAuthenticated(authentication) ?: return@get
        call.executeRelationship {
            useCases.listConnections(session.account.id, type).map { it.toResponse() }
        }
    }
    delete("/{connectionId}") {
        val session = call.requireAuthenticated(authentication) ?: return@delete
        val connectionId = call.requireUuid("connectionId") ?: return@delete
        call.executeRelationship {
            useCases.terminateConnection(session.account.id, connectionId, type).toResponse()
        }
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrReject(): T? =
    runCatching { receive<T>() }.getOrElse {
        respond(HttpStatusCode.BadRequest, RelationshipErrorResponse("invalid_request"))
        null
    }

private suspend fun ApplicationCall.requireUuid(parameter: String): UUID? {
    val id = parameters[parameter]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (id == null) respond(HttpStatusCode.BadRequest, RelationshipErrorResponse("invalid_id"))
    return id
}

private suspend fun ApplicationCall.executeRelationship(
    successStatus: HttpStatusCode = HttpStatusCode.OK,
    block: () -> Any,
) {
    try {
        respond(successStatus, block())
    } catch (exception: RelationshipException) {
        val status = when (exception.kind) {
            RelationshipErrorKind.BAD_REQUEST -> HttpStatusCode.BadRequest
            RelationshipErrorKind.FORBIDDEN -> HttpStatusCode.Forbidden
            RelationshipErrorKind.NOT_FOUND -> HttpStatusCode.NotFound
            RelationshipErrorKind.CONFLICT -> HttpStatusCode.Conflict
        }
        respond(status, RelationshipErrorResponse(exception.errorCode))
    }
}
