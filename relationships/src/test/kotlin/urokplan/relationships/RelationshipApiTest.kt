package urokplan.relationships

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import urokplan.relationships.http.ConnectionRequestResponse
import urokplan.relationships.http.ConnectionResponse
import urokplan.relationships.http.CreateGuardianRequest
import urokplan.relationships.http.CreateLearningRequest
import urokplan.relationships.http.GuardianActingRoleDto
import urokplan.relationships.http.LearningActingRoleDto
import urokplan.relationships.http.RelationshipErrorResponse
import urokplan.relationships.http.relationshipRoutes
import java.time.Clock
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RelationshipApiTest {
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
            changelog = "db/changelog/relationships-test-master.yaml",
        )
        database.transaction {
            exec(
                "TRUNCATE TABLE relationships.connections, relationships.connection_requests, " +
                    "identity.sessions, identity.account_roles, identity.accounts",
            )
        }
    }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `only recipient accepts learning request and active pair rejects duplicates`() = testApplication {
        applicationWithModules()
        val client = jsonClient()
        val teacher = client.register("teacher", AccountRoleDto.TEACHER)
        val student = client.register("student", AccountRoleDto.STUDENT)
        val teacherToken = client.login("teacher")
        val studentToken = client.login("student")

        val created = client.post("/api/learning-connections/requests") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(student.publicId, LearningActingRoleDto.TEACHER))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val request = created.body<ConnectionRequestResponse>()

        val requesterDecision = client.post("/api/learning-connections/requests/${request.id}/accept") {
            bearer(teacherToken)
        }
        assertEquals(HttpStatusCode.Forbidden, requesterDecision.status)
        assertEquals("only_recipient_can_decide", requesterDecision.body<RelationshipErrorResponse>().code)

        val accepted = client.post("/api/learning-connections/requests/${request.id}/accept") {
            bearer(studentToken)
        }
        assertEquals(HttpStatusCode.OK, accepted.status)
        assertEquals("ACTIVE", accepted.body<ConnectionResponse>().status)

        val duplicate = client.post("/api/learning-connections/requests") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(request.student.publicId, LearningActingRoleDto.TEACHER))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("connection_already_active", duplicate.body<RelationshipErrorResponse>().code)
    }

    @Test
    fun `terminated learning connection can be restored without deleting history`() = testApplication {
        applicationWithModules()
        val client = jsonClient()
        val teacher = client.register("restore-teacher", AccountRoleDto.TEACHER)
        val student = client.register("restore-student", AccountRoleDto.STUDENT)
        val teacherToken = client.login("restore-teacher")
        val studentToken = client.login("restore-student")

        val firstRequest = client.createLearningRequest(teacherToken, student.publicId, LearningActingRoleDto.TEACHER)
        val firstConnection = client.acceptLearningRequest(studentToken, firstRequest.id)
        val terminated = client.delete("/api/learning-connections/${firstConnection.id}") { bearer(studentToken) }
        assertEquals(HttpStatusCode.OK, terminated.status)
        assertEquals("TERMINATED", terminated.body<ConnectionResponse>().status)

        val restoredRequest = client.createLearningRequest(studentToken, teacher.publicId, LearningActingRoleDto.STUDENT)
        val restored = client.acceptLearningRequest(teacherToken, restoredRequest.id)
        assertEquals("ACTIVE", restored.status)

        val history = database.transaction {
            exec(
                "SELECT status, COUNT(*) FROM relationships.connections GROUP BY status ORDER BY status",
            ) { rows ->
                buildMap {
                    while (rows.next()) put(rows.getString(1), rows.getInt(2))
                }
            }
        }
        assertEquals(mapOf("ACTIVE" to 1, "TERMINATED" to 1), history)
    }

    @Test
    fun `student can initiate guardian request and parent can accept`() = testApplication {
        applicationWithModules()
        val client = jsonClient()
        val parent = client.register("parent", AccountRoleDto.PARENT)
        client.register("child", AccountRoleDto.STUDENT)
        val parentToken = client.login("parent")
        val childToken = client.login("child")

        val created = client.post("/api/guardian-connections/requests") {
            bearer(childToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGuardianRequest(parent.publicId, GuardianActingRoleDto.STUDENT))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val request = created.body<ConnectionRequestResponse>()
        val accepted = client.post("/api/guardian-connections/requests/${request.id}/accept") {
            bearer(parentToken)
        }
        assertEquals(HttpStatusCode.OK, accepted.status)
        assertEquals(parent.publicId, accepted.body<ConnectionResponse>().owner.publicId)
    }

    @Test
    fun `pending request and self connection are rejected`() = testApplication {
        applicationWithModules()
        val client = jsonClient()
        val teacher = client.register("pending-teacher", AccountRoleDto.TEACHER)
        val student = client.register("pending-student", AccountRoleDto.STUDENT)
        val mixed = client.register("mixed", AccountRoleDto.TEACHER, AccountRoleDto.STUDENT)
        val teacherToken = client.login("pending-teacher")
        val mixedToken = client.login("mixed")

        client.createLearningRequest(teacherToken, student.publicId, LearningActingRoleDto.TEACHER)
        val duplicate = client.post("/api/learning-connections/requests") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(student.publicId, LearningActingRoleDto.TEACHER))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("request_already_pending", duplicate.body<RelationshipErrorResponse>().code)

        val self = client.post("/api/learning-connections/requests") {
            bearer(mixedToken)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(mixed.publicId, LearningActingRoleDto.TEACHER))
        }
        assertEquals(HttpStatusCode.BadRequest, self.status)
        assertEquals("self_connection_forbidden", self.body<RelationshipErrorResponse>().code)
    }

    private fun ApplicationTestBuilder.applicationWithModules() {
        val clock = Clock.systemUTC()
        val identity = IdentityModule.create(database, clock)
        val relationships = RelationshipsModule.create(database, identity.api, clock)
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                identityRoutes(identity.useCases, identity.authentication)
                relationshipRoutes(relationships.useCases, identity.authentication)
            }
        }
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun io.ktor.client.HttpClient.register(
        login: String,
        vararg roles: AccountRoleDto,
    ): AccountResponse {
        val response = post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequest(login, login, "strong-password", roles.toSet()))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun io.ktor.client.HttpClient.login(login: String): String {
        val response = post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(login, "strong-password"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body<LoginResponse>().accessToken
    }

    private suspend fun io.ktor.client.HttpClient.createLearningRequest(
        token: String,
        targetPublicId: String,
        actingAs: LearningActingRoleDto,
    ): ConnectionRequestResponse {
        val response = post("/api/learning-connections/requests") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(targetPublicId, actingAs))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun io.ktor.client.HttpClient.acceptLearningRequest(
        token: String,
        requestId: String,
    ): ConnectionResponse {
        val response = post("/api/learning-connections/requests/$requestId/accept") { bearer(token) }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

}
