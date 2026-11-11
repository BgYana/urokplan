package urokplan.courses

import urokplan.courses.application.CourseService
import urokplan.courses.application.CourseUseCases
import urokplan.courses.application.CoursesApi
import urokplan.courses.application.GroupScheduleUpdater
import urokplan.courses.infrastructure.ExposedCourseRepository
import urokplan.database.TransactionRunner
import urokplan.identity.application.IdentityApi
import urokplan.relationships.application.RelationshipsApi
import java.time.Clock

class CoursesModule private constructor(
    val useCases: CourseUseCases,
    val api: CoursesApi,
) {
    companion object {
        fun create(
            transactions: TransactionRunner,
            identity: IdentityApi,
            relationships: RelationshipsApi,
            clock: Clock,
            groupSchedules: GroupScheduleUpdater,
        ): CoursesModule {
            val service = CourseService(
                transactions,
                ExposedCourseRepository(),
                identity,
                relationships,
                groupSchedules,
                clock,
            )
            return CoursesModule(useCases = service, api = service)
        }
    }
}
