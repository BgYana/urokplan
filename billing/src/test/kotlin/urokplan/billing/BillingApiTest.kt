package urokplan.billing

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import urokplan.billing.http.CreatePaymentRequest
import urokplan.billing.http.BillingErrorResponse
import urokplan.billing.http.PaymentResponse
import urokplan.billing.http.PaymentSummaryResponse
import urokplan.billing.http.MockPaymentResultResponse
import urokplan.billing.http.ResolvePaymentRequest
import urokplan.billing.http.billingRoutes
import urokplan.billing.http.mockPaymentRoutes
import urokplan.courses.CoursesModule
import urokplan.courses.http.AddEnrollmentRequest
import urokplan.courses.http.CourseFormatDto
import urokplan.courses.http.CourseResponse
import urokplan.courses.http.CreateCourseRequest
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
import urokplan.relationships.http.CreateGuardianRequest
import urokplan.relationships.http.GuardianActingRoleDto
import urokplan.relationships.http.LearningActingRoleDto
import urokplan.relationships.http.relationshipRoutes
import urokplan.scheduling.SchedulingModule
import urokplan.scheduling.http.CreateLessonRequest
import urokplan.scheduling.http.LessonResponse
import urokplan.scheduling.http.ParticipantPriceRequest
import urokplan.scheduling.http.UpdateAttendanceRequest
import urokplan.scheduling.http.schedulingRoutes
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingApiTest {
    private val postgres = PostgreSQLContainer("postgres:17-alpine")
    private lateinit var database: DatabaseHandle
    private lateinit var clock: MutableClock

    @BeforeAll
    fun startPostgres() = postgres.start()

    @AfterAll
    fun stopPostgres() = postgres.stop()

    @BeforeEach
    fun connectDatabase() {
        clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        database = DatabaseFactory.start(
            DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password, 2),
            changelog = "db/changelog/billing-test-master.yaml",
        )
        database.transaction {
            exec(
                "TRUNCATE TABLE billing.lesson_charges, billing.ledger_entries, billing.payments, billing.balances, " +
                    "scheduling.attendance_history, scheduling.lesson_participants, scheduling.lessons, " +
                    "scheduling.lesson_series, scheduling.schedule_locks, courses.group_memberships, " +
                    "courses.groups, courses.enrollments, courses.courses, relationships.connections, " +
                    "relationships.connection_requests, identity.sessions, identity.account_roles, identity.accounts",
            )
        }
    }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `successful result covers oldest debts once and failed result changes nothing`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        client.createLesson(fixture, "2026-01-02T10:00:00Z")
        client.createLesson(fixture, "2026-01-03T10:00:00Z")
        clock.current = Instant.parse("2026-01-04T00:00:00Z")

        val pending = client.createPayment(fixture, 5)
        assertEquals("mock", pending.providerName)
        assertEquals("mock-${pending.id}", pending.providerPaymentId)
        assertEquals(null, pending.confirmationUrl)
        val before = client.summary(fixture)
        assertEquals(0, before.availableUnits)
        assertEquals(listOf("OPEN", "OPEN"), before.charges.map { it.status })
        assertEquals(listOf(true, true), before.charges.map { it.overdue })

        val concurrentResults = coroutineScope {
            List(2) {
                async { client.resolvePayment(pending.providerPaymentId, "SUCCESS") }
            }.awaitAll()
        }
        assertEquals(listOf("SUCCEEDED", "SUCCEEDED"), concurrentResults.map { it.status })
        val after = client.summary(fixture)
        assertEquals(3, after.availableUnits)
        assertEquals(listOf("COVERED", "COVERED"), after.charges.map { it.status })

        client.resolvePayment(pending.providerPaymentId, "SUCCESS")
        val topUps = database.transaction {
            exec("SELECT COUNT(*) FROM billing.ledger_entries WHERE type = 'TOP_UP'") { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
        assertEquals(1, topUps)

        val failed = client.createPayment(fixture, 2)
        assertEquals("FAILED", client.resolvePayment(failed.providerPaymentId, "FAILURE").status)
        assertEquals(3, client.summary(fixture).availableUnits)
    }

    @Test
    fun `attendance correction refunds and reapplies unit with immutable ledger`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        val lesson = client.createLesson(fixture, "2026-01-02T10:00:00Z")
        val payment = client.createPayment(fixture, 1)
        client.resolvePayment(payment.providerPaymentId, "SUCCESS")
        clock.current = Instant.parse("2026-01-02T12:00:00Z")

        assertEquals(0, client.summary(fixture).availableUnits)
        client.updateAttendance(fixture, lesson.id, "EXCUSED_ABSENCE")
        val refunded = client.summary(fixture)
        assertEquals(1, refunded.availableUnits)
        assertEquals("WAIVED", refunded.charges.single().status)

        client.updateAttendance(fixture, lesson.id, "PRESENT")
        val reapplied = client.summary(fixture)
        assertEquals(0, reapplied.availableUnits)
        assertEquals("COVERED", reapplied.charges.single().status)
        assertEquals(
            listOf("TOP_UP", "CONSUMPTION", "REFUND", "CONSUMPTION"),
            reapplied.ledger.map { it.type },
        )
    }

    @Test
    fun `linked parent can create payment but teacher cannot`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        val parentPayment = client.post("/api/payments") {
            bearer(fixture.parentToken)
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentRequest(fixture.course.id, fixture.student.publicId, 2))
        }
        assertEquals(HttpStatusCode.Created, parentPayment.status)
        assertEquals(fixture.parent.publicId, parentPayment.body<PaymentResponse>().payerPublicId)

        val teacherPayment = client.post("/api/payments") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentRequest(fixture.course.id, fixture.student.publicId, 2))
        }
        assertEquals(HttpStatusCode.Forbidden, teacherPayment.status)
        assertEquals("student_or_parent_required", teacherPayment.body<BillingErrorResponse>().code)
    }

    private suspend fun ApplicationTestBuilder.applicationWithFixture(): Fixture {
        val identity = IdentityModule.create(database, clock)
        val relationships = RelationshipsModule.create(database, identity.api, clock)
        val groupSchedules = SchedulingModule.createGroupScheduleUpdater(database, clock)
        val courses = CoursesModule.create(database, identity.api, relationships.api, clock, groupSchedules)
        val attendanceBilling = BillingModule.createAttendanceUpdater(database, clock)
        val scheduling = SchedulingModule.create(
            database, identity.api, relationships.api, courses.api, attendanceBilling, clock,
        )
        val billing = BillingModule.create(
            database, identity.api, relationships.api, courses.api, scheduling.api, clock,
        )
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                identityRoutes(identity.useCases, identity.authentication)
                relationshipRoutes(relationships.useCases, identity.authentication)
                courseRoutes(courses.useCases, identity.authentication)
                schedulingRoutes(scheduling.useCases, identity.authentication)
                billingRoutes(billing.useCases, identity.authentication)
                billing.mockPayments?.let { mockPaymentRoutes(it) }
            }
        }
        val client = jsonClient()
        val teacher = client.register("billing-teacher", AccountRoleDto.TEACHER, "Europe/Moscow")
        val student = client.register("billing-student", AccountRoleDto.STUDENT, "UTC")
        val parent = client.register("billing-parent", AccountRoleDto.PARENT, "UTC")
        val teacherToken = client.login("billing-teacher")
        val studentToken = client.login("billing-student")
        val parentToken = client.login("billing-parent")
        val request = client.post("/api/learning-connections/requests") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(student.publicId, LearningActingRoleDto.TEACHER))
        }.body<ConnectionRequestResponse>()
        client.post("/api/learning-connections/requests/${request.id}/accept") { bearer(studentToken) }
        val guardianRequest = client.post("/api/guardian-connections/requests") {
            bearer(parentToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGuardianRequest(student.publicId, GuardianActingRoleDto.PARENT))
        }.body<ConnectionRequestResponse>()
        client.post("/api/guardian-connections/requests/${guardianRequest.id}/accept") { bearer(studentToken) }
        val course = client.post("/api/courses") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateCourseRequest("Оплата", "Математика", CourseFormatDto.INDIVIDUAL))
        }.body<CourseResponse>()
        client.post("/api/courses/${course.id}/enrollments") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(AddEnrollmentRequest(student.publicId, 100_000))
        }
        return Fixture(teacher, student, parent, teacherToken, studentToken, parentToken, course)
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun io.ktor.client.HttpClient.register(
        login: String,
        role: AccountRoleDto,
        billingTimeZone: String,
    ): AccountResponse = post("/api/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterAccountRequest(login, login, "strong-password", setOf(role), billingTimeZone))
    }.body()

    private suspend fun io.ktor.client.HttpClient.login(login: String): String =
        post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(login, "strong-password"))
        }.body<LoginResponse>().accessToken

    private suspend fun io.ktor.client.HttpClient.createLesson(fixture: Fixture, startsAt: String): LessonResponse =
        post("/api/lessons") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateLessonRequest(
                    fixture.course.id,
                    startsAt = startsAt,
                    durationMinutes = 60,
                    participants = listOf(ParticipantPriceRequest(fixture.student.publicId)),
                ),
            )
        }.body()

    private suspend fun io.ktor.client.HttpClient.createPayment(fixture: Fixture, units: Int): PaymentResponse =
        post("/api/payments") {
            bearer(fixture.studentToken)
            contentType(ContentType.Application.Json)
            setBody(CreatePaymentRequest(fixture.course.id, fixture.student.publicId, units))
        }.body()

    private suspend fun io.ktor.client.HttpClient.resolvePayment(
        providerPaymentId: String,
        outcome: String,
    ): MockPaymentResultResponse = post("/api/mock-payments/$providerPaymentId/result") {
        contentType(ContentType.Application.Json)
        setBody(ResolvePaymentRequest(outcome))
    }.body()

    private suspend fun io.ktor.client.HttpClient.summary(fixture: Fixture): PaymentSummaryResponse =
        get("/api/courses/${fixture.course.id}/students/${fixture.student.publicId}/payment-summary") {
            bearer(fixture.studentToken)
        }.body()

    private suspend fun io.ktor.client.HttpClient.updateAttendance(
        fixture: Fixture,
        lessonId: String,
        status: String,
    ) {
        val response = put(
            "/api/lessons/$lessonId/participants/${fixture.student.publicId}/attendance",
        ) {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(UpdateAttendanceRequest(status, "Корректировка оплаты"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private data class Fixture(
        val teacher: AccountResponse,
        val student: AccountResponse,
        val parent: AccountResponse,
        val teacherToken: String,
        val studentToken: String,
        val parentToken: String,
        val course: CourseResponse,
    )

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
        override fun instant(): Instant = current
    }
}
