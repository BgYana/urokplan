package urokplan.learning

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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
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
import urokplan.learning.http.AssessmentRequest
import urokplan.learning.http.AssessmentSummaryResponse
import urokplan.learning.http.AssignmentResponse
import urokplan.learning.http.CreateAssignmentRequest
import urokplan.learning.http.FeedbackRequest
import urokplan.learning.http.FeedbackResponse
import urokplan.learning.http.ReviewSubmissionRequest
import urokplan.learning.http.SaveDraftRequest
import urokplan.learning.http.SubmissionResponse
import urokplan.learning.http.learningRoutes
import urokplan.relationships.RelationshipsModule
import urokplan.relationships.http.ConnectionRequestResponse
import urokplan.relationships.http.CreateGuardianRequest
import urokplan.relationships.http.CreateLearningRequest
import urokplan.relationships.http.GuardianActingRoleDto
import urokplan.relationships.http.LearningActingRoleDto
import urokplan.relationships.http.relationshipRoutes
import urokplan.scheduling.SchedulingModule
import urokplan.scheduling.application.AttendanceBillingUpdater
import urokplan.scheduling.application.LessonReference
import urokplan.scheduling.application.ParticipantReference
import urokplan.scheduling.http.CreateLessonRequest
import urokplan.scheduling.http.LessonResponse
import urokplan.scheduling.http.ParticipantPriceRequest
import urokplan.scheduling.http.schedulingRoutes
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LearningApiTest {
    private val postgres = PostgreSQLContainer("postgres:17-alpine")
    private lateinit var database: DatabaseHandle
    private val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))

    @BeforeAll
    fun startPostgres() = postgres.start()

    @AfterAll
    fun stopPostgres() = postgres.stop()

    @BeforeEach
    fun connectDatabase() {
        clock.current = Instant.parse("2026-01-01T00:00:00Z")
        database = DatabaseFactory.start(
            DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password, 2),
            changelog = "db/changelog/learning-test-master.yaml",
        )
        database.transaction {
            exec(
                "TRUNCATE TABLE learning.feedback, learning.assessments, learning.submission_links, " +
                    "learning.submission_attempts, learning.assignment_targets, learning.assignment_links, " +
                    "learning.assignments, scheduling.attendance_history, scheduling.lesson_participants, " +
                    "scheduling.lessons, scheduling.lesson_series, scheduling.schedule_locks, " +
                    "courses.group_memberships, courses.groups, " +
                    "courses.enrollments, courses.courses, relationships.connections, relationships.connection_requests, " +
                    "identity.sessions, identity.account_roles, identity.accounts CASCADE",
            )
        }
    }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `homework keeps attempts and parent can read but not submit`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        val assignment = client.post("/api/assignments") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateAssignmentRequest(
                    fixture.lesson.id,
                    "Solve exercises",
                    listOf("https://example.org/task"),
                    "2026-01-02T09:00:00Z",
                    setOf(fixture.student.publicId),
                ),
            )
        }.body<AssignmentResponse>()

        clock.current = Instant.parse("2026-01-03T00:00:00Z")
        val draft = client.put("/api/assignments/${assignment.id}/submissions/draft") {
            bearer(fixture.studentToken)
            contentType(ContentType.Application.Json)
            setBody(SaveDraftRequest("First answer"))
        }.body<SubmissionResponse>()
        assertEquals("DRAFT", draft.status)
        val submitted = client.post("/api/assignments/${assignment.id}/submissions") {
            bearer(fixture.studentToken)
        }.body<SubmissionResponse>()
        assertTrue(submitted.submittedLate)

        client.put("/api/submissions/${submitted.id}/review") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(ReviewSubmissionRequest("REVISION_REQUESTED", "Fix the proof", 5))
        }
        val secondDraft = client.put("/api/assignments/${assignment.id}/submissions/draft") {
            bearer(fixture.studentToken)
            contentType(ContentType.Application.Json)
            setBody(SaveDraftRequest("Corrected answer"))
        }.body<SubmissionResponse>()
        assertEquals(2, secondDraft.attemptNumber)
        val second = client.post("/api/assignments/${assignment.id}/submissions") {
            bearer(fixture.studentToken)
        }.body<SubmissionResponse>()
        client.put("/api/submissions/${second.id}/review") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(ReviewSubmissionRequest("ACCEPTED", "Done", 9))
        }

        val parentView = client.get("/api/assignments/${assignment.id}") {
            bearer(fixture.parentToken)
        }.body<AssignmentResponse>()
        assertEquals(listOf("REVISION_REQUESTED", "ACCEPTED"), parentView.students.single().submissions.map { it.status })

        val parentSubmit = client.post("/api/assignments/${assignment.id}/submissions") {
            bearer(fixture.parentToken)
        }
        assertEquals(HttpStatusCode.Forbidden, parentSubmit.status)
    }

    @Test
    fun `assessment versions use latest average and feedback stays private`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        clock.current = Instant.parse("2026-01-02T12:00:00Z")

        client.put("/api/lessons/${fixture.lesson.id}/participants/${fixture.student.publicId}/assessment") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(AssessmentRequest(7, "First mark"))
        }
        val corrected = client.put(
            "/api/lessons/${fixture.lesson.id}/participants/${fixture.student.publicId}/assessment",
        ) {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(AssessmentRequest(9, "Corrected"))
        }.body<AssessmentSummaryResponse>()
        assertEquals(2, corrected.assessments.size)
        assertEquals(9.0, corrected.averageScore)

        client.put("/api/lessons/${fixture.lesson.id}/feedback") {
            bearer(fixture.studentToken)
            contentType(ContentType.Application.Json)
            setBody(FeedbackRequest(4, 3, "Private note"))
        }
        val teacherFeedback = client.get("/api/lessons/${fixture.lesson.id}/feedback") {
            bearer(fixture.teacherToken)
        }.body<List<FeedbackResponse>>()
        assertEquals("Private note", teacherFeedback.single().comment)

        val parentFeedback = client.get("/api/lessons/${fixture.lesson.id}/feedback") {
            bearer(fixture.parentToken)
        }
        assertEquals(HttpStatusCode.NotFound, parentFeedback.status)
    }

    private suspend fun ApplicationTestBuilder.applicationWithFixture(): Fixture {
        val identity = IdentityModule.create(database, clock)
        val relationships = RelationshipsModule.create(database, identity.api, clock)
        val groupSchedules = SchedulingModule.createGroupScheduleUpdater(database, clock)
        val courses = CoursesModule.create(database, identity.api, relationships.api, clock, groupSchedules)
        val scheduling = SchedulingModule.create(
            database,
            identity.api,
            relationships.api,
            courses.api,
            object : AttendanceBillingUpdater {
                override fun attendanceChanged(lesson: LessonReference, participant: ParticipantReference) = Unit
            },
            clock,
        )
        val learning = LearningModule.create(
            database, identity.api, relationships.api, courses.api, scheduling.api, clock,
        )
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                identityRoutes(identity.useCases, identity.authentication)
                relationshipRoutes(relationships.useCases, identity.authentication)
                courseRoutes(courses.useCases, identity.authentication)
                schedulingRoutes(scheduling.useCases, identity.authentication)
                learningRoutes(learning.useCases, identity.authentication)
            }
        }
        val client = jsonClient()
        val teacher = client.register("learning-teacher", AccountRoleDto.TEACHER)
        val student = client.register("learning-student", AccountRoleDto.STUDENT)
        val parent = client.register("learning-parent", AccountRoleDto.PARENT)
        val teacherToken = client.login("learning-teacher")
        val studentToken = client.login("learning-student")
        val parentToken = client.login("learning-parent")

        val learningRequest = client.post("/api/learning-connections/requests") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(student.publicId, LearningActingRoleDto.TEACHER))
        }.body<ConnectionRequestResponse>()
        client.post("/api/learning-connections/requests/${learningRequest.id}/accept") { bearer(studentToken) }
        val guardianRequest = client.post("/api/guardian-connections/requests") {
            bearer(parentToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGuardianRequest(student.publicId, GuardianActingRoleDto.PARENT))
        }.body<ConnectionRequestResponse>()
        client.post("/api/guardian-connections/requests/${guardianRequest.id}/accept") { bearer(studentToken) }
        val course = client.post("/api/courses") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateCourseRequest("Learning", "Math", CourseFormatDto.INDIVIDUAL))
        }.body<CourseResponse>()
        client.post("/api/courses/${course.id}/enrollments") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(AddEnrollmentRequest(student.publicId, 100_000))
        }
        val lesson = client.post("/api/lessons") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateLessonRequest(
                    course.id,
                    startsAt = "2026-01-02T10:00:00Z",
                    durationMinutes = 60,
                    participants = listOf(ParticipantPriceRequest(student.publicId)),
                ),
            )
        }.body<LessonResponse>()
        return Fixture(teacher, student, teacherToken, studentToken, parentToken, lesson)
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun io.ktor.client.HttpClient.register(login: String, role: AccountRoleDto): AccountResponse =
        post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterAccountRequest(login, login, "strong-password", setOf(role)))
        }.body()

    private suspend fun io.ktor.client.HttpClient.login(login: String): String =
        post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(login, "strong-password"))
        }.body<LoginResponse>().accessToken

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private data class Fixture(
        val teacher: AccountResponse,
        val student: AccountResponse,
        val teacherToken: String,
        val studentToken: String,
        val parentToken: String,
        val lesson: LessonResponse,
    )

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
        override fun instant(): Instant = current
    }
}
