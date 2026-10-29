package urokplan.app

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.routing
import urokplan.identity.IdentityModule
import urokplan.identity.http.identityRoutes
import urokplan.database.DatabaseConfig
import urokplan.database.DatabaseFactory
import urokplan.relationships.RelationshipsModule
import urokplan.relationships.http.relationshipRoutes
import urokplan.courses.CoursesModule
import urokplan.courses.http.courseRoutes
import urokplan.scheduling.SchedulingModule
import urokplan.scheduling.http.schedulingRoutes
import urokplan.billing.BillingModule
import urokplan.billing.http.billingRoutes
import urokplan.billing.http.mockPaymentRoutes
import urokplan.learning.LearningModule
import urokplan.learning.http.learningRoutes
import java.time.Clock

fun Application.module() {
    val database = DatabaseFactory.start(
        DatabaseConfig(
            jdbcUrl = environment.config.property("database.jdbcUrl").getString(),
            username = environment.config.property("database.username").getString(),
            password = environment.config.property("database.password").getString(),
            maximumPoolSize = environment.config.property("database.maximumPoolSize").getString().toInt(),
        ),
    )

    monitor.subscribe(ApplicationStopped) {
        database.close()
    }

    val clock = Clock.systemUTC()
    val identity = IdentityModule.create(database, clock)
    val relationships = RelationshipsModule.create(database, identity.api, clock)
    val groupSchedules = SchedulingModule.createGroupScheduleUpdater(database, clock)
    val courses = CoursesModule.create(database, identity.api, relationships.api, clock, groupSchedules)
    val attendanceBilling = BillingModule.createAttendanceUpdater(database, clock)
    val scheduling = SchedulingModule.create(
        database,
        identity.api,
        relationships.api,
        courses.api,
        attendanceBilling,
        clock,
    )
    val billing = BillingModule.create(
        database,
        identity.api,
        relationships.api,
        courses.api,
        scheduling.api,
        clock,
    )
    val learning = LearningModule.create(
        database,
        identity.api,
        relationships.api,
        courses.api,
        scheduling.api,
        clock,
    )

    configureHttp(database::isReady)
    routing {
        identityRoutes(identity.useCases, identity.authentication)
        relationshipRoutes(relationships.useCases, identity.authentication)
        courseRoutes(courses.useCases, identity.authentication)
        schedulingRoutes(scheduling.useCases, identity.authentication)
        billingRoutes(billing.useCases, identity.authentication)
        billing.mockPayments?.let { mockPaymentRoutes(it) }
        learningRoutes(learning.useCases, identity.authentication)
    }
}
