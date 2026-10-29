package urokplan.identity.http

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import urokplan.identity.application.AuthenticatedSession
import urokplan.identity.application.IdentityUseCases
import urokplan.identity.application.IdentityAuthentication
import urokplan.identity.application.InvalidCredentialsException
import urokplan.identity.application.LoginAlreadyExistsException
import urokplan.identity.application.RegistrationValidationException

fun Route.identityRoutes(
    useCases: IdentityUseCases,
    authentication: IdentityAuthentication,
) {
    route("/api") {
        route("/auth") {
            post("/register") {
                val request = call.receiveOrReject<RegisterAccountRequest>() ?: return@post
                try {
                    call.respond(HttpStatusCode.Created, useCases.register(request.toCommand()).toResponse())
                } catch (exception: RegistrationValidationException) {
                    call.respond(HttpStatusCode.BadRequest, IdentityErrorResponse(exception.errorCode))
                } catch (_: LoginAlreadyExistsException) {
                    call.respond(HttpStatusCode.Conflict, IdentityErrorResponse("login_already_exists"))
                }
            }

            post("/login") {
                val request = call.receiveOrReject<LoginRequest>() ?: return@post
                try {
                    call.respond(useCases.login(request.toCommand()).toResponse())
                } catch (_: InvalidCredentialsException) {
                    call.respond(HttpStatusCode.Unauthorized, IdentityErrorResponse("invalid_credentials"))
                }
            }

            post("/logout") {
                val authenticated = call.requireAuthenticated(authentication) ?: return@post
                useCases.logout(authenticated.sessionId)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        get("/me") {
            val authenticated = call.requireAuthenticated(authentication) ?: return@get
            call.respond(authenticated.account.toResponse())
        }

        patch("/me/billing-time-zone") {
            val authenticated = call.requireAuthenticated(authentication) ?: return@patch
            val request = call.receiveOrReject<UpdateBillingTimeZoneRequest>() ?: return@patch
            try {
                val account = useCases.updateBillingTimeZone(
                    authenticated.account.id,
                    request.billingTimeZone,
                )
                call.respond(account.toResponse())
            } catch (exception: RegistrationValidationException) {
                call.respond(HttpStatusCode.BadRequest, IdentityErrorResponse(exception.errorCode))
            }
        }

        get("/users/by-public-id/{publicId}") {
            call.requireAuthenticated(authentication) ?: return@get
            val publicId = call.parameters["publicId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                IdentityErrorResponse("public_id_required"),
            )
            val profile = useCases.findPublicProfile(publicId)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, IdentityErrorResponse("account_not_found"))
            } else {
                call.respond(profile.toResponse())
            }
        }
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrReject(): T? =
    runCatching { receive<T>() }.getOrElse {
        respond(HttpStatusCode.BadRequest, IdentityErrorResponse("invalid_request"))
        null
    }

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
