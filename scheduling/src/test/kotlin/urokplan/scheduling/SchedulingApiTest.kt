package urokplan.scheduling

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import urokplan.courses.http.AddGroupMemberRequest
import urokplan.courses.http.CourseFormatDto
import urokplan.courses.http.CourseErrorResponse
import urokplan.courses.http.CourseResponse
import urokplan.courses.http.CreateCourseRequest
import urokplan.courses.http.CreateGroupRequest
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
import urokplan.scheduling.http.CreateLessonRequest
import urokplan.scheduling.http.CreateSeriesRequest
import urokplan.scheduling.http.LessonParticipantResponse
import urokplan.scheduling.http.LessonResponse
import urokplan.scheduling.http.ParticipantPriceRequest
import urokplan.scheduling.http.RescheduleLessonRequest
import urokplan.scheduling.http.SchedulingErrorResponse
import urokplan.scheduling.http.SeriesResponse
import urokplan.scheduling.http.UpdateAttendanceRequest
import urokplan.scheduling.http.schedulingRoutes
import urokplan.scheduling.application.AttendanceBillingUpdater
import urokplan.scheduling.application.LessonReference
import urokplan.scheduling.application.ParticipantReference
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulingApiTest {
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
            changelog = "db/changelog/scheduling-test-master.yaml",
        )
        database.transaction {
            exec(
                "TRUNCATE TABLE scheduling.attendance_history, scheduling.lesson_participants, " +
                    "scheduling.lessons, scheduling.lesson_series, scheduling.schedule_locks, " +
                    "courses.group_memberships, courses.groups, courses.enrollments, courses.courses, " +
                    "relationships.connections, relationships.connection_requests, " +
                    "identity.sessions, identity.account_roles, identity.accounts",
            )
        }
    }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `rejects overlaps allows adjacent lesson and keeps price snapshot`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        val lesson = client.createLesson(
            fixture.teacherToken,
            fixture.course.id,
            fixture.student.publicId,
            "2026-01-02T10:00:00Z",
        )
        assertEquals(100_000, lesson.participants.single().priceMinor)

        val overlap = client.post("/api/lessons") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateLessonRequest(
                    fixture.course.id,
                    startsAt = "2026-01-02T10:30:00Z",
                    durationMinutes = 60,
                    participants = listOf(ParticipantPriceRequest(fixture.student.publicId)),
                ),
            )
        }
        assertEquals(HttpStatusCode.Conflict, overlap.status)
        assertEquals("schedule_conflict", overlap.body<SchedulingErrorResponse>().code)

        val adjacent = client.createLesson(
            fixture.teacherToken,
            fixture.course.id,
            fixture.student.publicId,
            "2026-01-02T11:00:00Z",
        )
        assertEquals("2026-01-02T11:00:00Z", adjacent.startsAt)

        client.patch("/api/courses/${fixture.course.id}/enrollments/${fixture.student.publicId}") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(UpdatePriceRequest(150_000))
        }
        val stored = client.get("/api/lessons/${lesson.id}") { bearer(fixture.studentToken) }
            .body<LessonResponse>()
        assertEquals(100_000, stored.participants.single().priceMinor)
    }

    @Test
    fun `series materializes zoned occurrences and one-off move preserves lesson id`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        val seriesResponse = client.post("/api/lesson-series") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateSeriesRequest(
                    courseId = fixture.course.id,
                    localTime = "10:00",
                    zoneId = "Europe/Moscow",
                    daysOfWeek = setOf("MONDAY"),
                    startDate = "2026-01-05",
                    endDate = "2026-01-12",
                    durationMinutes = 60,
                    participants = listOf(ParticipantPriceRequest(fixture.student.publicId)),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, seriesResponse.status)
        assertEquals(2, seriesResponse.body<SeriesResponse>().lessonCount)

        val before = client.get(
            "/api/lessons?from=2026-01-01T00:00:00Z&to=2026-02-01T00:00:00Z",
        ) { bearer(fixture.teacherToken) }.body<List<LessonResponse>>()
        assertEquals(listOf("2026-01-05T07:00:00Z", "2026-01-12T07:00:00Z"), before.map { it.startsAt })

        val moved = client.patch("/api/lessons/${before.first().id}/schedule") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(RescheduleLessonRequest("2026-01-06T08:00:00Z", 90, "THIS"))
        }.body<List<LessonResponse>>().single()
        assertEquals(before.first().id, moved.id)
        assertEquals("2026-01-06T08:00:00Z", moved.startsAt)

        val after = client.get(
            "/api/lessons?from=2026-01-01T00:00:00Z&to=2026-02-01T00:00:00Z",
        ) { bearer(fixture.teacherToken) }.body<List<LessonResponse>>()
        assertEquals("2026-01-12T07:00:00Z", after.last().startsAt)
    }

    @Test
    fun `attendance correction is audited after lesson ends`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        val lesson = client.createLesson(
            fixture.teacherToken,
            fixture.course.id,
            fixture.student.publicId,
            "2026-01-02T10:00:00Z",
        )
        clock.current = Instant.parse("2026-01-02T12:00:00Z")

        val present = client.updateAttendance(
            fixture.teacherToken,
            lesson.id,
            fixture.student.publicId,
            "PRESENT",
        )
        assertEquals("PRESENT", present.attendance)
        val corrected = client.updateAttendance(
            fixture.teacherToken,
            lesson.id,
            fixture.student.publicId,
            "EXCUSED_ABSENCE",
        )
        assertEquals("EXCUSED_ABSENCE", corrected.attendance)

        val historyCount = database.transaction {
            exec("SELECT COUNT(*) FROM scheduling.attendance_history") { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
        assertEquals(2, historyCount)
    }

    @Test
    fun `group composition updates only future lesson participants`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        val group = client.post("/api/groups") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(fixture.course.id, "Группа расписания"))
        }.body<GroupResponse>()
        client.post("/api/groups/${group.id}/members") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(AddGroupMemberRequest(fixture.student.publicId))
        }
        client.post("/api/lesson-series") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateSeriesRequest(
                    courseId = fixture.course.id,
                    groupId = group.id,
                    localTime = "10:00",
                    zoneId = "UTC",
                    daysOfWeek = setOf("MONDAY"),
                    startDate = "2026-01-05",
                    endDate = "2026-01-05",
                    durationMinutes = 60,
                ),
            )
        }

        val before = client.get(
            "/api/lessons?from=2026-01-01T00:00:00Z&to=2026-02-01T00:00:00Z",
        ) { bearer(fixture.teacherToken) }.body<List<LessonResponse>>().single()
        assertEquals(1, before.participants.size)

        val removed = client.delete("/api/groups/${group.id}/members/${fixture.student.publicId}") {
            bearer(fixture.teacherToken)
        }
        assertEquals(HttpStatusCode.NoContent, removed.status)
        val afterRemoval = client.get("/api/lessons/${before.id}") { bearer(fixture.teacherToken) }
            .body<LessonResponse>()
        assertEquals(0, afterRemoval.participants.size)

        val restored = client.post("/api/groups/${group.id}/members") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(AddGroupMemberRequest(fixture.student.publicId))
        }
        assertEquals(HttpStatusCode.Created, restored.status)
        val afterRestore = client.get("/api/lessons/${before.id}") { bearer(fixture.teacherToken) }
            .body<LessonResponse>()
        assertEquals(1, afterRestore.participants.size)
    }

    @Test
    fun `student with overlapping lesson cannot be added to scheduled group`() = testApplication {
        val fixture = applicationWithFixture()
        val client = jsonClient()
        val group = client.post("/api/groups") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(fixture.course.id, "Конфликтующая группа"))
        }.body<GroupResponse>()
        client.post("/api/groups/${group.id}/members") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(AddGroupMemberRequest(fixture.student.publicId))
        }
        client.post("/api/lesson-series") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateSeriesRequest(
                    courseId = fixture.course.id,
                    groupId = group.id,
                    localTime = "10:00",
                    zoneId = "UTC",
                    daysOfWeek = setOf("MONDAY"),
                    startDate = "2026-01-05",
                    endDate = "2026-01-05",
                    durationMinutes = 60,
                ),
            )
        }
        client.delete("/api/groups/${group.id}/members/${fixture.student.publicId}") {
            bearer(fixture.teacherToken)
        }

        val secondTeacher = client.register("second-schedule-teacher", AccountRoleDto.TEACHER)
        val secondTeacherToken = client.login("second-schedule-teacher")
        val request = client.post("/api/learning-connections/requests") {
            bearer(secondTeacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(fixture.student.publicId, LearningActingRoleDto.TEACHER))
        }.body<ConnectionRequestResponse>()
        client.post("/api/learning-connections/requests/${request.id}/accept") {
            bearer(fixture.studentToken)
        }
        val secondCourse = client.post("/api/courses") {
            bearer(secondTeacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateCourseRequest("Другой курс", "Физика", CourseFormatDto.INDIVIDUAL))
        }.body<CourseResponse>()
        client.post("/api/courses/${secondCourse.id}/enrollments") {
            bearer(secondTeacherToken)
            contentType(ContentType.Application.Json)
            setBody(AddEnrollmentRequest(fixture.student.publicId, 120_000))
        }
        client.createLesson(
            secondTeacherToken,
            secondCourse.id,
            fixture.student.publicId,
            "2026-01-05T10:00:00Z",
        )

        val conflict = client.post("/api/groups/${group.id}/members") {
            bearer(fixture.teacherToken)
            contentType(ContentType.Application.Json)
            setBody(AddGroupMemberRequest(fixture.student.publicId))
        }
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertEquals("schedule_conflict", conflict.body<CourseErrorResponse>().code)

        val activeMemberships = database.transaction {
            exec(
                "SELECT COUNT(*) FROM courses.group_memberships " +
                    "WHERE group_id = '${group.id}' AND status = 'ACTIVE'",
            ) { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
        assertEquals(0, activeMemberships)
        assertEquals(secondTeacher.publicId, secondCourse.teacher.publicId)
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
            TestAttendanceBillingUpdater,
            clock,
        )
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                identityRoutes(identity.useCases, identity.authentication)
                relationshipRoutes(relationships.useCases, identity.authentication)
                courseRoutes(courses.useCases, identity.authentication)
                schedulingRoutes(scheduling.useCases, identity.authentication)
            }
        }
        val client = jsonClient()
        return createFixture(client)
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun createFixture(client: io.ktor.client.HttpClient): Fixture {
        val teacher = client.register("schedule-teacher", AccountRoleDto.TEACHER)
        val student = client.register("schedule-student", AccountRoleDto.STUDENT)
        val teacherToken = client.login("schedule-teacher")
        val studentToken = client.login("schedule-student")
        val request = client.post("/api/learning-connections/requests") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateLearningRequest(student.publicId, LearningActingRoleDto.TEACHER))
        }.body<ConnectionRequestResponse>()
        client.post("/api/learning-connections/requests/${request.id}/accept") { bearer(studentToken) }
        val course = client.post("/api/courses") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(CreateCourseRequest("Расписание", "Математика", CourseFormatDto.GROUP))
        }.body<CourseResponse>()
        client.post("/api/courses/${course.id}/enrollments") {
            bearer(teacherToken)
            contentType(ContentType.Application.Json)
            setBody(AddEnrollmentRequest(student.publicId, 100_000))
        }
        return Fixture(teacher, student, teacherToken, studentToken, course)
    }

    private suspend fun io.ktor.client.HttpClient.register(
        login: String,
        role: AccountRoleDto,
    ): AccountResponse = post("/api/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterAccountRequest(login, login, "strong-password", setOf(role)))
    }.body()

    private suspend fun io.ktor.client.HttpClient.login(login: String): String =
        post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(login, "strong-password"))
        }.body<LoginResponse>().accessToken

    private suspend fun io.ktor.client.HttpClient.createLesson(
        token: String,
        courseId: String,
        studentPublicId: String,
        startsAt: String,
    ): LessonResponse {
        val response = post("/api/lessons") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateLessonRequest(
                    courseId,
                    startsAt = startsAt,
                    durationMinutes = 60,
                    participants = listOf(ParticipantPriceRequest(studentPublicId)),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun io.ktor.client.HttpClient.updateAttendance(
        token: String,
        lessonId: String,
        studentPublicId: String,
        status: String,
    ): LessonParticipantResponse = put(
        "/api/lessons/$lessonId/participants/$studentPublicId/attendance",
    ) {
        bearer(token)
        contentType(ContentType.Application.Json)
        setBody(UpdateAttendanceRequest(status, "Исправление преподавателя"))
    }.body()

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private data class Fixture(
        val teacher: AccountResponse,
        val student: AccountResponse,
        val teacherToken: String,
        val studentToken: String,
        val course: CourseResponse,
    )

    private object TestAttendanceBillingUpdater : AttendanceBillingUpdater {
        override fun attendanceChanged(lesson: LessonReference, participant: ParticipantReference) = Unit
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
        override fun instant(): Instant = current
    }
}
