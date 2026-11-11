package urokplan.identity.http

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import urokplan.identity.application.AuthenticatedSession
import urokplan.identity.application.IdentityAuthentication

public suspend fun ApplicationCall.requireAuthenticated(
    authentication: IdentityAuthentication,
): AuthenticatedSession? {
    val authorization = request.headers[HttpHeaders.Authorization]
    val token = authorization
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substringAfter(' ')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val authenticated = token?.let(authentication::resolveSession)
    if (authenticated == null) {
        respond(HttpStatusCode.Unauthorized, IdentityErrorResponse("unauthorized"))
    }
    return authenticated
}
