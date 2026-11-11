package urokplan.billing.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import urokplan.billing.application.BillingErrorKind
import urokplan.billing.application.BillingException
import urokplan.billing.application.BillingUseCases
import urokplan.billing.application.PaymentPreviewCommand
import urokplan.identity.application.IdentityAuthentication
import urokplan.identity.http.requireAuthenticated
import java.util.UUID

fun Route.billingRoutes(
    useCases: BillingUseCases,
    authentication: IdentityAuthentication,
) {
    route("/api/payments") {
        post {
            val session = call.requireAuthenticated(authentication) ?: return@post
            val request = call.receiveOrReject<CreatePaymentRequest>() ?: return@post
            val command = call.parseOrReject(request::toCommand) ?: return@post
            call.executeBilling(HttpStatusCode.Created) {
                useCases.createPayment(session.account.id, command).toResponse()
            }
        }
        get("/{paymentId}") {
            val session = call.requireAuthenticated(authentication) ?: return@get
            val paymentId = call.requireUuid("paymentId") ?: return@get
            call.executeBilling { useCases.getPayment(session.account.id, paymentId).toResponse() }
        }
    }

    get("/api/courses/{courseId}/students/{studentPublicId}/payment-summary") {
        val session = call.requireAuthenticated(authentication) ?: return@get
        val courseId = call.requireUuid("courseId") ?: return@get
        val studentPublicId = call.parameters["studentPublicId"] ?: return@get call.invalidRequest()
        call.executeBilling {
            useCases.getSummary(session.account.id, courseId, studentPublicId).toResponse()
        }
    }

    post("/api/courses/{courseId}/students/{studentPublicId}/payment-preview") {
        val session = call.requireAuthenticated(authentication) ?: return@post
        val courseId = call.requireUuid("courseId") ?: return@post
        val studentPublicId = call.parameters["studentPublicId"] ?: return@post call.invalidRequest()
        val request = call.receiveOrReject<PaymentPreviewRequest>() ?: return@post
        call.executeBilling {
            useCases.preview(
                session.account.id,
                PaymentPreviewCommand(courseId, studentPublicId, request.units),
            ).toResponse()
        }
    }
}

internal suspend inline fun <reified T : Any> ApplicationCall.receiveOrReject(): T? =
    runCatching { receive<T>() }.getOrElse {
        invalidRequest()
        null
    }

internal suspend fun <T> ApplicationCall.parseOrReject(block: () -> T): T? =
    runCatching(block).getOrElse {
        invalidRequest()
        null
    }

internal suspend fun ApplicationCall.requireUuid(name: String): UUID? {
    val id = parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (id == null) invalidRequest()
    return id
}

internal suspend fun ApplicationCall.invalidRequest() {
    respond(HttpStatusCode.BadRequest, BillingErrorResponse("invalid_request"))
}

internal suspend fun ApplicationCall.executeBilling(
    successStatus: HttpStatusCode = HttpStatusCode.OK,
    block: () -> Any,
) {
    try {
        respond(successStatus, block())
    } catch (exception: BillingException) {
        val status = when (exception.kind) {
            BillingErrorKind.BAD_REQUEST -> HttpStatusCode.BadRequest
            BillingErrorKind.FORBIDDEN -> HttpStatusCode.Forbidden
            BillingErrorKind.NOT_FOUND -> HttpStatusCode.NotFound
            BillingErrorKind.CONFLICT -> HttpStatusCode.Conflict
        }
        respond(status, BillingErrorResponse(exception.errorCode))
    }
}
