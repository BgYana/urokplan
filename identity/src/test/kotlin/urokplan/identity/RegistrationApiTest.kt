package urokplan.identity

import de.mkammerer.argon2.Argon2Factory
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.routing
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
import urokplan.identity.http.AccountResponse
import urokplan.identity.http.AccountRoleDto
import urokplan.identity.http.IdentityErrorResponse
import urokplan.identity.http.LoginRequest
import urokplan.identity.http.LoginResponse
import urokplan.identity.http.PublicAccountResponse
import urokplan.identity.http.RegisterAccountRequest
import urokplan.identity.http.UpdateBillingTimeZoneRequest
import urokplan.identity.http.identityRoutes
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrationApiTest {
    private val postgres = PostgreSQLContainer("postgres:17-alpine")
    private lateinit var database: DatabaseHandle

    @BeforeAll
    fun startPostgres() {
        postgres.start()
    }

    @AfterAll
    fun stopPostgres() {
        postgres.stop()
    }

    @BeforeEach
    fun connectDatabase() {
        database = DatabaseFactory.start(
            DatabaseConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
                maximumPoolSize = 2,
            ),
            changelog = "db/changelog/identity-test-master.yaml",
        )
        database.transaction {
            exec("TRUNCATE TABLE identity.sessions, identity.account_roles, identity.accounts")
        }
    }

    @AfterEach
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `registers account with hashed password and selected roles`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterAccountRequest(
                    login = "teacher.anna",
                    displayName = "Анна Иванова",
                    password = "strong-password",
                    roles = setOf(AccountRoleDto.TEACHER, AccountRoleDto.STUDENT),
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val account = response.body<AccountResponse>()
        assertEquals("teacher.anna", account.login)
        assertEquals("Анна Иванова", account.displayName)
        assertEquals(listOf(AccountRoleDto.STUDENT, AccountRoleDto.TEACHER), account.roles)
        assertTrue(account.publicId.isNotBlank())

        val storedHash = database.transaction {
            exec("SELECT password_hash FROM identity.accounts") { result ->
                result.next()
                result.getString(1)
            }
        }
        requireNotNull(storedHash)
        assertNotEquals("strong-password", storedHash)
        assertTrue(storedHash.startsWith("\$argon2id\$"))
        val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
        assertTrue(argon2.verify(storedHash, "strong-password".toCharArray()))
    }

    @Test
    fun `rejects duplicate login`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()
        val request = RegisterAccountRequest(
            login = "same-login",
            displayName = "Одинаковый пользователь",
            password = "strong-password",
            roles = setOf(AccountRoleDto.PARENT),
        )

        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val duplicate = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("login_already_exists", duplicate.body<IdentityErrorResponse>().code)
    }

    @Test
    fun `validates registration fields`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()

        val invalidLogin = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequest("a", "Имя", "strong-password", setOf(AccountRoleDto.STUDENT)))
        }
        val invalidPassword = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequest("valid-login", "Имя", "short", setOf(AccountRoleDto.STUDENT)))
        }
        val missingRoles = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequest("valid-login", "Имя", "strong-password", emptySet()))
        }
        val invalidDisplayName = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequest("valid-login", " ", "strong-password", setOf(AccountRoleDto.STUDENT)))
        }

        assertEquals("invalid_login", invalidLogin.body<IdentityErrorResponse>().code)
        assertEquals("invalid_password", invalidPassword.body<IdentityErrorResponse>().code)
        assertEquals("roles_required", missingRoles.body<IdentityErrorResponse>().code)
        assertEquals("invalid_display_name", invalidDisplayName.body<IdentityErrorResponse>().code)
    }

    @Test
    fun `login creates hashed session and me returns current account`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()
        client.register("session-user")

        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("session-user", "strong-password"))
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val login = loginResponse.body<LoginResponse>()
        assertEquals("Bearer", login.tokenType)
        assertEquals("session-user", login.account.login)
        assertTrue(login.accessToken.length >= 40)
        val storedTokenHash = database.transaction {
            exec("SELECT token_hash FROM identity.sessions") { result ->
                result.next()
                result.getString(1)
            }
        }
        requireNotNull(storedTokenHash)
        assertNotEquals(login.accessToken, storedTokenHash)
        assertEquals(64, storedTokenHash.length)

        val me = client.get("/api/me") {
            header(HttpHeaders.Authorization, "Bearer ${login.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals("session-user", me.body<AccountResponse>().login)
    }

    @Test
    fun `authenticated user changes billing time zone`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()
        client.register("moving-user")
        val login = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("moving-user", "strong-password"))
        }.body<LoginResponse>()

        val updated = client.patch("/api/me/billing-time-zone") {
            header(HttpHeaders.Authorization, "Bearer ${login.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(UpdateBillingTimeZoneRequest("Asia/Yekaterinburg"))
        }

        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals("Asia/Yekaterinburg", updated.body<AccountResponse>().billingTimeZone)

        val invalid = client.patch("/api/me/billing-time-zone") {
            header(HttpHeaders.Authorization, "Bearer ${login.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(UpdateBillingTimeZoneRequest("not-a-zone"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals("invalid_billing_time_zone", invalid.body<IdentityErrorResponse>().code)
    }

    @Test
    fun `login does not reveal whether login or password is wrong`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()
        client.register("known-user")

        val wrongPassword = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("known-user", "wrong-password"))
        }
        val unknownLogin = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("unknown-user", "wrong-password"))
        }

        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(HttpStatusCode.Unauthorized, unknownLogin.status)
        assertEquals("invalid_credentials", wrongPassword.body<IdentityErrorResponse>().code)
        assertEquals("invalid_credentials", unknownLogin.body<IdentityErrorResponse>().code)
    }

    @Test
    fun `logout revokes only current session`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()
        client.register("multi-session-user")
        val first = client.login("multi-session-user")
        val second = client.login("multi-session-user")
        assertNotEquals(first.accessToken, second.accessToken)

        val logout = client.post("/api/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer ${first.accessToken}")
        }

        assertEquals(HttpStatusCode.NoContent, logout.status)
        val revokedMe = client.get("/api/me") {
            header(HttpHeaders.Authorization, "Bearer ${first.accessToken}")
        }
        val activeMe = client.get("/api/me") {
            header(HttpHeaders.Authorization, "Bearer ${second.accessToken}")
        }
        assertEquals(HttpStatusCode.Unauthorized, revokedMe.status)
        assertEquals(HttpStatusCode.OK, activeMe.status)
    }

    @Test
    fun `expired and missing sessions are unauthorized`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()
        client.register("expired-user")
        val login = client.login("expired-user")
        database.transaction {
            exec("UPDATE identity.sessions SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'")
        }

        val expired = client.get("/api/me") {
            header(HttpHeaders.Authorization, "Bearer ${login.accessToken}")
        }
        val missing = client.get("/api/me")

        assertEquals(HttpStatusCode.Unauthorized, expired.status)
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("unauthorized", expired.body<IdentityErrorResponse>().code)
    }

    @Test
    fun `authenticated user finds minimal profile without login`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()
        val target = client.register("private-login", "Целевой пользователь")
        client.register("searching-user")
        val session = client.login("searching-user")

        val response = client.get("/api/users/by-public-id/${target.publicId}") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val profile = Json.decodeFromString<PublicAccountResponse>(body)
        assertEquals(target.publicId, profile.publicId)
        assertEquals("Целевой пользователь", profile.displayName)
        assertEquals(listOf(AccountRoleDto.STUDENT), profile.roles)
        assertTrue("private-login" !in body)
    }

    @Test
    fun `profile search requires session and returns not found for unknown id`() = testApplication {
        applicationWithIdentity()
        val client = jsonClient()
        client.register("searching-user")
        val session = client.login("searching-user")

        val anonymous = client.get("/api/users/by-public-id/missing")
        val missing = client.get("/api/users/by-public-id/missing") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }

        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("account_not_found", missing.body<IdentityErrorResponse>().code)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.applicationWithIdentity() {
        val identity = IdentityModule.create(database, Clock.systemUTC())
        application {
            install(ServerContentNegotiation) {
                json()
            }
            routing {
                identityRoutes(identity.useCases, identity.authentication)
            }
        }
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    private suspend fun io.ktor.client.HttpClient.register(
        login: String,
        displayName: String = "Пользователь",
    ): AccountResponse {
        val response = post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequest(login, displayName, "strong-password", setOf(AccountRoleDto.STUDENT)))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun io.ktor.client.HttpClient.login(login: String): LoginResponse {
        val response = post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(login, "strong-password"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
    }
}
