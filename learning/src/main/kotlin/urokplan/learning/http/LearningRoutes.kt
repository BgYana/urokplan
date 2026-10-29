package urokplan.learning.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import urokplan.identity.application.IdentityAuthentication
import urokplan.identity.http.requireAuthenticated
import urokplan.learning.application.LearningErrorKind
import urokplan.learning.application.LearningException
import urokplan.learning.application.LearningUseCases
import java.util.UUID

fun Route.learningRoutes(
    useCases: LearningUseCases,
    authentication: IdentityAuthentication,
) {
    post("/api/assignments") {
        val session = call.requireAuthenticated(authentication) ?: return@post
        val request = call.receiveOrReject<CreateAssignmentRequest>() ?: return@post
        val command = call.parseOrReject(request::toCommand) ?: return@post
        call.executeLearning(HttpStatusCode.Created) {
            useCases.createAssignment(session.account.id, command).toResponse()
        }
    }

    get("/api/assignments") {
        val session = call.requireAuthenticated(authentication) ?: return@get
        val lessonId = call.optionalUuid("lessonId") ?: if (call.parameters["lessonId"] != null) return@get else null
        call.executeLearning {
            useCases.listAssignments(session.account.id, lessonId).map { it.toResponse() }
        }
    }

    get("/api/assignments/{assignmentId}") {
        val session = call.requireAuthenticated(authentication) ?: return@get
        val assignmentId = call.requireUuid("assignmentId") ?: return@get
        call.executeLearning { useCases.getAssignment(session.account.id, assignmentId).toResponse() }
    }

    get("/api/assignments/{assignmentId}/submissions") {
        val session = call.requireAuthenticated(authentication) ?: return@get
        val assignmentId = call.requireUuid("assignmentId") ?: return@get
        call.executeLearning {
            useCases.getAssignment(session.account.id, assignmentId).students.map { it.toResponse() }
        }
    }

    put("/api/assignments/{assignmentId}/submissions/draft") {
        val session = call.requireAuthenticated(authentication) ?: return@put
        val assignmentId = call.requireUuid("assignmentId") ?: return@put
        val request = call.receiveOrReject<SaveDraftRequest>() ?: return@put
        call.executeLearning {
            useCases.saveDraft(session.account.id, assignmentId, request.toCommand()).toResponse()
        }
    }

    post("/api/assignments/{assignmentId}/submissions") {
        val session = call.requireAuthenticated(authentication) ?: return@post
        val assignmentId = call.requireUuid("assignmentId") ?: return@post
        call.executeLearning {
            useCases.submit(session.account.id, assignmentId).toResponse()
        }
    }

    put("/api/submissions/{submissionId}/review") {
        val session = call.requireAuthenticated(authentication) ?: return@put
        val submissionId = call.requireUuid("submissionId") ?: return@put
        val request = call.receiveOrReject<ReviewSubmissionRequest>() ?: return@put
        val command = call.parseOrReject(request::toCommand) ?: return@put
        call.executeLearning {
            useCases.reviewSubmission(session.account.id, submissionId, command).toResponse()
        }
    }

    put("/api/lessons/{lessonId}/participants/{studentPublicId}/assessment") {
        val session = call.requireAuthenticated(authentication) ?: return@put
        val lessonId = call.requireUuid("lessonId") ?: return@put
        val studentPublicId = call.parameters["studentPublicId"] ?: return@put call.invalidRequest()
        val request = call.receiveOrReject<AssessmentRequest>() ?: return@put
        call.executeLearning {
            useCases.putAssessment(session.account.id, lessonId, studentPublicId, request.toCommand()).toResponse()
        }
    }

    get("/api/lessons/{lessonId}/participants/{studentPublicId}/assessment") {
        val session = call.requireAuthenticated(authentication) ?: return@get
        val lessonId = call.requireUuid("lessonId") ?: return@get
        val studentPublicId = call.parameters["studentPublicId"] ?: return@get call.invalidRequest()
        call.executeLearning {
            useCases.getAssessment(session.account.id, lessonId, studentPublicId).toResponse()
        }
    }

    get("/api/assessments") {
        val session = call.requireAuthenticated(authentication) ?: return@get
        val courseId = call.requireUuid("courseId") ?: return@get
        val studentPublicId = call.request.queryParameters["studentPublicId"] ?: return@get call.invalidRequest()
        call.executeLearning {
            useCases.listAssessments(session.account.id, courseId, studentPublicId).toResponse()
        }
    }

    put("/api/lessons/{lessonId}/feedback") {
        val session = call.requireAuthenticated(authentication) ?: return@put
        val lessonId = call.requireUuid("lessonId") ?: return@put
        val request = call.receiveOrReject<FeedbackRequest>() ?: return@put
        call.executeLearning {
            useCases.putFeedback(session.account.id, lessonId, request.toCommand()).toResponse()
        }
    }

    get("/api/lessons/{lessonId}/feedback") {
        val session = call.requireAuthenticated(authentication) ?: return@get
        val lessonId = call.requireUuid("lessonId") ?: return@get
        call.executeLearning {
            useCases.getFeedback(session.account.id, lessonId).map { it.toResponse() }
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

private suspend fun ApplicationCall.requireUuid(name: String): UUID? {
    val raw = parameters[name] ?: request.queryParameters[name]
    val value = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (value == null) invalidRequest()
    return value
}

private suspend fun ApplicationCall.optionalUuid(name: String): UUID? {
    val raw = request.queryParameters[name] ?: return null
    return runCatching { UUID.fromString(raw) }.getOrElse {
        invalidRequest()
        null
    }
}

private suspend fun ApplicationCall.invalidRequest() {
    respond(HttpStatusCode.BadRequest, LearningErrorResponse("invalid_request"))
}

private suspend fun ApplicationCall.executeLearning(
    successStatus: HttpStatusCode = HttpStatusCode.OK,
    block: () -> Any,
) {
    try {
        respond(successStatus, block())
    } catch (exception: LearningException) {
        val status = when (exception.kind) {
            LearningErrorKind.BAD_REQUEST -> HttpStatusCode.BadRequest
            LearningErrorKind.FORBIDDEN -> HttpStatusCode.Forbidden
            LearningErrorKind.NOT_FOUND -> HttpStatusCode.NotFound
            LearningErrorKind.CONFLICT -> HttpStatusCode.Conflict
        }
        respond(status, LearningErrorResponse(exception.errorCode))
    }
}
