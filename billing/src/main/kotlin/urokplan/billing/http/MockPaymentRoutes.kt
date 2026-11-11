package urokplan.billing.http

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import urokplan.billing.application.MockPaymentUseCases
import java.util.UUID

fun Route.mockPaymentRoutes(useCases: MockPaymentUseCases) {
    post("/api/mock-payments/{providerPaymentId}/result") {
        val providerPaymentId = call.requireMockProviderPaymentId() ?: return@post
        val request = call.receiveOrReject<ResolvePaymentRequest>() ?: return@post
        val outcome = call.parseOrReject(request::toOutcome) ?: return@post
        call.executeBilling {
            MockPaymentResultResponse(useCases.resolvePayment(providerPaymentId, outcome).status.name)
        }
    }
}

private suspend fun ApplicationCall.requireMockProviderPaymentId(): String? {
    val value = parameters["providerPaymentId"]
    val uuid = value?.removePrefix("mock-")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (uuid == null) {
        invalidRequest()
        return null
    }
    return "mock-$uuid"
}
