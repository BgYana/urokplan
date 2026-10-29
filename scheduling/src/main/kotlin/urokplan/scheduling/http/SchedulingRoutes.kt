package urokplan.scheduling.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import urokplan.identity.application.IdentityAuthentication
import urokplan.identity.http.requireAuthenticated
import urokplan.scheduling.application.SchedulingErrorKind
import urokplan.scheduling.application.SchedulingException
import urokplan.scheduling.application.SchedulingUseCases
import urokplan.scheduling.domain.ChangeScope
import java.time.Instant
import java.util.UUID

fun Route.schedulingRoutes(
    useCases: SchedulingUseCases,
    authentication: IdentityAuthentication,
) {
    route("/api/lessons") {
        post {
            val session = call.requireAuthenticated(authentication) ?: return@post
            val request = call.receiveOrReject<CreateLessonRequest>() ?: return@post
            val command = call.parseOrReject(request::toCommand) ?: return@post
            call.executeScheduling(HttpStatusCode.Created) {
                useCases.createLesson(session.account.id, command).toResponse()
            }
        }
        get {
            val session = call.requireAuthenticated(authentication) ?: return@get
            val from = call.parseInstantParameter("from") ?: return@get
            val to = call.parseInstantParameter("to") ?: return@get
            val courseId = call.parameters["courseId"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull() ?: return@get call.invalidRequest()
            }
            call.executeScheduling {
                useCases.listLessons(session.account.id, from, to, courseId).map { it.toResponse() }
            }
        }
        get("/{lessonId}") {
            val session = call.requireAuthenticated(authentication) ?: return@get
            val lessonId = call.requireUuid("lessonId") ?: return@get
            call.executeScheduling { useCases.getLesson(session.account.id, lessonId).toResponse() }
        }
        patch("/{lessonId}/schedule") {
            val session = call.requireAuthenticated(authentication) ?: return@patch
            val lessonId = call.requireUuid("lessonId") ?: return@patch
            val request = call.receiveOrReject<RescheduleLessonRequest>() ?: return@patch
            val command = call.parseOrReject(request::toCommand) ?: return@patch
            call.executeScheduling {
                useCases.rescheduleLesson(session.account.id, lessonId, command).map { it.toResponse() }
            }
        }
        post("/{lessonId}/cancel") {
            val session = call.requireAuthenticated(authentication) ?: return@post
            val lessonId = call.requireUuid("lessonId") ?: return@post
            val request = call.receiveOrReject<CancelLessonRequest>() ?: return@post
            val scope = call.parseOrReject { ChangeScope.valueOf(request.scope) } ?: return@post
            call.executeScheduling {
                useCases.cancelLesson(session.account.id, lessonId, scope).map { it.toResponse() }
            }
        }
        put("/{lessonId}/participants/{studentPublicId}/attendance") {
            val session = call.requireAuthenticated(authentication) ?: return@put
            val lessonId = call.requireUuid("lessonId") ?: return@put
            val studentPublicId = call.parameters["studentPublicId"] ?: return@put call.invalidRequest()
            val request = call.receiveOrReject<UpdateAttendanceRequest>() ?: return@put
            val command = call.parseOrReject(request::toCommand) ?: return@put
            call.executeScheduling {
                useCases.updateAttendance(session.account.id, lessonId, studentPublicId, command).toResponse()
            }
        }
    }

    post("/api/lesson-series") {
        val session = call.requireAuthenticated(authentication) ?: return@post
        val request = call.receiveOrReject<CreateSeriesRequest>() ?: return@post
        val command = call.parseOrReject(request::toCommand) ?: return@post
        call.executeScheduling(HttpStatusCode.Created) {
            useCases.createSeries(session.account.id, command).toResponse()
        }
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrReject(): T? =
    runCatching { receive<T>() }.getOrElse {
        invalidRequest()
        null
    }

private suspend fun <T> ApplicationCall.parseOrReject(block: () -> T): T? =
    runCatching(block).getOrElse {
        invalidRequest()
        null
    }

private suspend fun ApplicationCall.parseInstantParameter(name: String): Instant? {
    val value = parameters[name]?.let { runCatching { Instant.parse(it) }.getOrNull() }
    if (value == null) invalidRequest()
    return value
}

private suspend fun ApplicationCall.requireUuid(name: String): UUID? {
    val value = parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (value == null) invalidRequest()
    return value
}

private suspend fun ApplicationCall.invalidRequest() {
    respond(HttpStatusCode.BadRequest, SchedulingErrorResponse("invalid_request"))
}

private suspend fun ApplicationCall.executeScheduling(
    successStatus: HttpStatusCode = HttpStatusCode.OK,
    block: () -> Any,
) {
    try {
        respond(successStatus, block())
    } catch (exception: SchedulingException) {
        val status = when (exception.kind) {
            SchedulingErrorKind.BAD_REQUEST -> HttpStatusCode.BadRequest
            SchedulingErrorKind.FORBIDDEN -> HttpStatusCode.Forbidden
            SchedulingErrorKind.NOT_FOUND -> HttpStatusCode.NotFound
            SchedulingErrorKind.CONFLICT -> HttpStatusCode.Conflict
        }
        respond(status, SchedulingErrorResponse(exception.errorCode))
    }
}
