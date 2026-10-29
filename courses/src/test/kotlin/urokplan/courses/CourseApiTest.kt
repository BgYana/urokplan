package urokplan.courses

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import urokplan.courses.application.GroupScheduleUpdater
import urokplan.courses.http.AddEnrollmentRequest
import urokplan.courses.http.AddGroupMemberRequest
import urokplan.courses.http.CourseErrorResponse
import urokplan.courses.http.CourseFormatDto
import urokplan.courses.http.CourseResponse
import urokplan.courses.http.CreateCourseRequest
import urokplan.courses.http.CreateGroupRequest
import urokplan.courses.http.EnrollmentResponse
import urokplan.courses.http.GroupMemberResponse
import urokplan.courses.http.GroupResponse
import urokplan.courses.http.UpdatePriceRequest
import urokplan.courses.http.courseRoutes
import urokplan.database.DatabaseConfig
import urokplan.database.DatabaseFactory
import urokplan.database.DatabaseHandle
import urokplan.identity.IdentityModule
import urokplan.identity.http.AccountResponse
import urokplan.identity.http.AccountRoleDto
import urokplan.identity.http.LoginRequest
import urokplan.identity.http.LoginResponse
import urokplan.identity.http.RegisterAccountRequest
import urokplan.identity.http.identityRoutes
import urokplan.relationships.RelationshipsModule
import urokplan.relationships.http.ConnectionRequestResponse
import urokplan.relationships.http.CreateLearningRequest
import urokplan.relationships.http.LearningActingRoleDto
import urokplan.relationships.http.relationshipRoutes
import java.time.Clock
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourseApiTest {
    private val postgres = PostgreSQLContainer("postgres:17-alpine")
    private lateinit var database: DatabaseHandle

    @BeforeAll
    fun startPostgres() = postgres.start()

    @AfterAll
    fun stopPostgres() = postgres.stop()

    @BeforeEach
    fun connectDatabase() {
        database = DatabaseFactory.start(
            DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password, 2),
            changelog = "db/changelog/courses-test-master.yaml",
        )
        database.transaction {
            exec(
                "TRUNCATE TABLE courses.group_memberships, courses.groups, courses.enrollments, courses.courses, " +
                    "relationships.connections, relationships.connection_requests, " +
                    "identity.sessions, identity.account_roles, identity.accounts",
            )
        }
    }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `teacher enrolls only connected student and student sees own course`() = testApplication {
        applicationWithModules()
        val client = jsonClient()
        val teacher = client.register("course-teacher", AccountRoleDto.TEACHER)
        val student = client.register("course-student", AccountRoleDto.STUDENT)
        val outsider = client.register("course-outsider", AccountRoleDto.STUDENT)
        val teacherToken = client.login("course-teacher")
        val studentToken = client.login("course-student")
        val course = client.createCourse(teacherToken, CourseFormatDto.GROUP)

        val disconnected = client.addEnrollment(teacherToken, course.id, outsider.publicId, 150_000)
        assertEquals(HttpStatusCode.Forbidden, disconnected.status)
        assertEquals("active_learning_connection_required", disconnected.body<CourseErrorResponse>().code)

        client.connectTeacherAndStudent(teacherToken, studentToken, student.publicId)
        val enrolled = client.addEnrollment(teacherToken, course.id, student.publicId, 150_000)
        assertEquals(HttpStatusCode.Created, enrolled.status)
        assertEquals(150_000, enrolled.body<EnrollmentResponse>().priceMinor)

        val visible = client.get("/api/courses") { bearer(studentToken) }
        assertEquals(HttpStatusCode.OK, visible.status)
        assertEquals(listOf(course.id), visible.body<List<CourseResponse>>().map { it.id })
        assertEquals(teacher.publicId, visible.body<List<CourseResponse>>().single().teacher.publicId)
    }

    @Test
    fun `individual course has one active student and bulk price affects active enrollments`() = testApplication {
        applicationWithModules()
        val client = jsonClient()
        val first = client.register("first-student", AccountRoleDto.STUDENT)
        val second = client.register("second-student", AccountRoleDto.STUDENT)
        client.register("individual-teacher", AccountRoleDto.TEACHER)
        val teacherToken = client.login("individual-teacher")
        val firstToken = client.login("first-student")
        val secondToken = client.login("second-student")
        client.connectTeacherAndStudent(teacherToken, firstToken, first.publicId)
        client.connectTeacherAndStudent(teacherToken, secondToken, second.publicId)
        val course = client.createCourse(teacherToken, CourseFormatDto.INDIVIDUAL)

        assertEquals(HttpStatusCode.Created, client.addEnrollment(teacherToken, course.id, first.publicId, 100_000).status)
        val secondEnrollment = client.addEnrollment(teacherToken, course.id, second.publicId, 100_000)
        assertEquals(HttpStatusCode.Conflict, secondEnrollment.status)
        assertEquals("individual_course_already_has_student", secondEnrollment.body<CourseErrorResponse>().code)

        val prices = client.patch("/api/courses/${course.id}/enrollments/price") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(UpdatePriceRequest(125_000))
        }
        assertEquals(HttpStatusCode.OK, prices.status)
        assertEquals(125_000, prices.body<List<EnrollmentResponse>>().single().priceMinor)
    }

    @Test
    fun `group composition changes preserve membership history`() = testApplication {
        applicationWithModules()
        val client = jsonClient()
        val student = client.register("group-student", AccountRoleDto.STUDENT)
        client.register("group-teacher", AccountRoleDto.TEACHER)
        val teacherToken = client.login("group-teacher")
        val studentToken = client.login("group-student")
        client.connectTeacherAndStudent(teacherToken, studentToken, student.publicId)
        val course = client.createCourse(teacherToken, CourseFormatDto.GROUP)
        client.addEnrollment(teacherToken, course.id, student.publicId, 90_000)
        val firstGroup = client.createGroup(teacherToken, course.id, "Первая группа")
        val secondGroup = client.createGroup(teacherToken, course.id, "Вторая группа")

        val firstMembership = client.addGroupMember(teacherToken, firstGroup.id, student.publicId)
        assertEquals(HttpStatusCode.Created, firstMembership.status)
        val duplicate = client.addGroupMember(teacherToken, secondGroup.id, student.publicId)
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val removed = client.delete("/api/groups/${firstGroup.id}/members/${student.publicId}") {
            bearer(teacherToken)
        }
        assertEquals(HttpStatusCode.NoContent, removed.status)
        assertEquals(HttpStatusCode.Created, client.addGroupMember(teacherToken, secondGroup.id, student.publicId).status)

        val historyCount = database.transaction {
            exec("SELECT COUNT(*) FROM courses.group_memberships") { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
        assertEquals(2, historyCount)
        val studentGroups = client.get("/api/groups") { bearer(studentToken) }.body<List<GroupResponse>>()
        assertEquals(listOf(secondGroup.id), studentGroups.map { it.id })
    }

    private fun ApplicationTestBuilder.applicationWithModules() {
        val clock = Clock.systemUTC()
        val identity = IdentityModule.create(database, clock)
        val relationships = RelationshipsModule.create(database, identity.api, clock)
        val courses = CoursesModule.create(
            database,
            identity.api,
            relationships.api,
            clock,
            TestGroupScheduleUpdater,
        )
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                identityRoutes(identity.useCases, identity.authentication)
                relationshipRoutes(relationships.useCases, identity.authentication)
                courseRoutes(courses.useCases, identity.authentication)
            }
        }
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun io.ktor.client.HttpClient.register(
        login: String,
        vararg roles: AccountRoleDto,
    ): AccountResponse = post("/api/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterAccountRequest(login, login, "strong-password", roles.toSet()))
    }.body()

    private suspend fun io.ktor.client.HttpClient.login(login: String): String =
        post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(login, "strong-password"))
        }.body<LoginResponse>().accessToken

    private suspend fun io.ktor.client.HttpClient.createCourse(
        token: String,
        format: CourseFormatDto,
    ): CourseResponse {
        val response = post("/api/courses") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(CreateCourseRequest("Математика", "Алгебра", format, "https://meet.example/course"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun io.ktor.client.HttpClient.addEnrollment(
        token: String,
        courseId: String,
        studentPublicId: String,
        priceMinor: Long,
    ) = post("/api/courses/$courseId/enrollments") {
        bearer(token)
        contentType(ContentType.Application.Json)
        setBody(AddEnrollmentRequest(studentPublicId, priceMinor))
    }

    private suspend fun io.ktor.client.HttpClient.connectTeacherAndStudent(
        teacherToken: String,
        studentToken: String,
        studentPublicId: String,
    ) {
        val request = post("/api/learning-connections/requests") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(studentPublicId, LearningActingRoleDto.TEACHER))
        }.body<ConnectionRequestResponse>()
        val accepted = post("/api/learning-connections/requests/${request.id}/accept") { bearer(studentToken) }
        assertEquals(HttpStatusCode.OK, accepted.status)
    }

    private suspend fun io.ktor.client.HttpClient.createGroup(
        token: String,
        courseId: String,
        name: String,
    ): GroupResponse = post("/api/groups") {
        bearer(token)
        contentType(ContentType.Application.Json)
        setBody(CreateGroupRequest(courseId, name))
    }.body()

    private suspend fun io.ktor.client.HttpClient.addGroupMember(
        token: String,
        groupId: String,
        studentPublicId: String,
    ) = post("/api/groups/$groupId/members") {
        bearer(token)
        contentType(ContentType.Application.Json)
        setBody(AddGroupMemberRequest(studentPublicId))
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private object TestGroupScheduleUpdater : GroupScheduleUpdater {
        override fun addStudentToFutureLessons(groupId: UUID, studentId: UUID, priceMinor: Long) = Unit
        override fun removeStudentFromFutureLessons(groupId: UUID, studentId: UUID) = Unit
        override fun removeStudentFromCourseFutureGroupLessons(courseId: UUID, studentId: UUID) = Unit
    }
}
