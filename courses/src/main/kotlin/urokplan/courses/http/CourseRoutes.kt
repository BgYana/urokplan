package urokplan.courses.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import urokplan.courses.application.CourseErrorKind
import urokplan.courses.application.CourseException
import urokplan.courses.application.CourseUseCases
import urokplan.courses.application.CreateGroupCommand
import urokplan.identity.application.IdentityAuthentication
import urokplan.identity.http.requireAuthenticated
import java.util.UUID

fun Route.courseRoutes(
    useCases: CourseUseCases,
    authentication: IdentityAuthentication,
) {
    route("/api/courses") {
        post {
            val session = call.requireAuthenticated(authentication) ?: return@post
            val request = call.receiveOrReject<CreateCourseRequest>() ?: return@post
            call.executeCourse(HttpStatusCode.Created) {
                useCases.createCourse(session.account.id, request.toCommand()).toResponse()
            }
        }
        get {
            val session = call.requireAuthenticated(authentication) ?: return@get
            call.executeCourse { useCases.listCourses(session.account.id).map { it.toResponse() } }
        }
        get("/{courseId}") {
            val session = call.requireAuthenticated(authentication) ?: return@get
            val courseId = call.requireUuid("courseId") ?: return@get
            call.executeCourse { useCases.getCourse(session.account.id, courseId).toResponse() }
        }
        patch("/{courseId}") {
            val session = call.requireAuthenticated(authentication) ?: return@patch
            val courseId = call.requireUuid("courseId") ?: return@patch
            val request = call.receiveOrReject<UpdateCourseRequest>() ?: return@patch
            call.executeCourse { useCases.updateCourse(session.account.id, courseId, request.toCommand()).toResponse() }
        }
        post("/{courseId}/enrollments") {
            val session = call.requireAuthenticated(authentication) ?: return@post
            val courseId = call.requireUuid("courseId") ?: return@post
            val request = call.receiveOrReject<AddEnrollmentRequest>() ?: return@post
            call.executeCourse(HttpStatusCode.Created) {
                useCases.addEnrollment(session.account.id, courseId, request.toCommand()).toResponse()
            }
        }
        get("/{courseId}/enrollments") {
            val session = call.requireAuthenticated(authentication) ?: return@get
            val courseId = call.requireUuid("courseId") ?: return@get
            call.executeCourse { useCases.listEnrollments(session.account.id, courseId).map { it.toResponse() } }
        }
        patch("/{courseId}/enrollments/price") {
            val session = call.requireAuthenticated(authentication) ?: return@patch
            val courseId = call.requireUuid("courseId") ?: return@patch
            val request = call.receiveOrReject<UpdatePriceRequest>() ?: return@patch
            call.executeCourse {
                useCases.updateAllEnrollmentPrices(session.account.id, courseId, request.priceMinor)
                    .map { it.toResponse() }
            }
        }
        patch("/{courseId}/enrollments/{studentPublicId}") {
            val session = call.requireAuthenticated(authentication) ?: return@patch
            val courseId = call.requireUuid("courseId") ?: return@patch
            val studentPublicId = call.parameters["studentPublicId"] ?: return@patch call.invalidId()
            val request = call.receiveOrReject<UpdatePriceRequest>() ?: return@patch
            call.executeCourse {
                useCases.updateEnrollmentPrice(session.account.id, courseId, studentPublicId, request.priceMinor)
                    .toResponse()
            }
        }
        delete("/{courseId}/enrollments/{studentPublicId}") {
            val session = call.requireAuthenticated(authentication) ?: return@delete
            val courseId = call.requireUuid("courseId") ?: return@delete
            val studentPublicId = call.parameters["studentPublicId"] ?: return@delete call.invalidId()
            call.executeCourse(HttpStatusCode.NoContent) {
                useCases.removeEnrollment(session.account.id, courseId, studentPublicId)
                Unit
            }
        }
    }

    route("/api/groups") {
        post {
            val session = call.requireAuthenticated(authentication) ?: return@post
            val request = call.receiveOrReject<CreateGroupRequest>() ?: return@post
            val courseId = runCatching { UUID.fromString(request.courseId) }.getOrNull()
                ?: return@post call.invalidId()
            call.executeCourse(HttpStatusCode.Created) {
                useCases.createGroup(
                    session.account.id,
                    CreateGroupCommand(courseId, request.name, request.callLink),
                ).toResponse()
            }
        }
        get {
            val session = call.requireAuthenticated(authentication) ?: return@get
            call.executeCourse { useCases.listGroups(session.account.id).map { it.toResponse() } }
        }
        get("/{groupId}") {
            val session = call.requireAuthenticated(authentication) ?: return@get
            val groupId = call.requireUuid("groupId") ?: return@get
            call.executeCourse { useCases.getGroup(session.account.id, groupId).toResponse() }
        }
        patch("/{groupId}") {
            val session = call.requireAuthenticated(authentication) ?: return@patch
            val groupId = call.requireUuid("groupId") ?: return@patch
            val request = call.receiveOrReject<UpdateGroupRequest>() ?: return@patch
            call.executeCourse { useCases.updateGroup(session.account.id, groupId, request.toCommand()).toResponse() }
        }
        post("/{groupId}/members") {
            val session = call.requireAuthenticated(authentication) ?: return@post
            val groupId = call.requireUuid("groupId") ?: return@post
            val request = call.receiveOrReject<AddGroupMemberRequest>() ?: return@post
            call.executeCourse(HttpStatusCode.Created) {
                useCases.addGroupMember(session.account.id, groupId, request.studentPublicId).toResponse()
            }
        }
        get("/{groupId}/members") {
            val session = call.requireAuthenticated(authentication) ?: return@get
            val groupId = call.requireUuid("groupId") ?: return@get
            call.executeCourse { useCases.listGroupMembers(session.account.id, groupId).map { it.toResponse() } }
        }
        delete("/{groupId}/members/{studentPublicId}") {
            val session = call.requireAuthenticated(authentication) ?: return@delete
            val groupId = call.requireUuid("groupId") ?: return@delete
            val studentPublicId = call.parameters["studentPublicId"] ?: return@delete call.invalidId()
            call.executeCourse(HttpStatusCode.NoContent) {
                useCases.removeGroupMember(session.account.id, groupId, studentPublicId)
                Unit
            }
        }
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrReject(): T? =
    runCatching { receive<T>() }.getOrElse {
        respond(HttpStatusCode.BadRequest, CourseErrorResponse("invalid_request"))
        null
    }

private suspend fun ApplicationCall.requireUuid(parameter: String): UUID? {
    val id = parameters[parameter]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (id == null) invalidId()
    return id
}

private suspend fun ApplicationCall.invalidId() {
    respond(HttpStatusCode.BadRequest, CourseErrorResponse("invalid_id"))
}

private suspend fun ApplicationCall.executeCourse(
    successStatus: HttpStatusCode = HttpStatusCode.OK,
    block: () -> Any,
) {
    try {
        if (successStatus == HttpStatusCode.NoContent) {
            block()
            respond(successStatus)
        } else {
            respond(successStatus, block())
        }
    } catch (exception: CourseException) {
        val status = when (exception.kind) {
            CourseErrorKind.BAD_REQUEST -> HttpStatusCode.BadRequest
            CourseErrorKind.FORBIDDEN -> HttpStatusCode.Forbidden
            CourseErrorKind.NOT_FOUND -> HttpStatusCode.NotFound
            CourseErrorKind.CONFLICT -> HttpStatusCode.Conflict
        }
        respond(status, CourseErrorResponse(exception.errorCode))
    }
}
