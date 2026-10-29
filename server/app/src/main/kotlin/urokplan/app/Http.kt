package urokplan.app

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

fun Application.configureHttp(databaseReady: () -> Boolean) {
    install(ContentNegotiation) {
        json()
    }
    install(StatusPages) {
        exception<Exception> { call, cause ->
            this@configureHttp.log.error("Unhandled error for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_server_error"))
        }
    }

    routing {
        route("/api") {
            get("/health") {
                if (databaseReady()) {
                    call.respond(HealthResponse(status = "UP", database = "UP"))
                } else {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        HealthResponse(status = "DOWN", database = "DOWN"),
                    )
                }
            }
        }
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
)

@Serializable
data class ErrorResponse(val code: String)
