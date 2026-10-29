package urokplan.scheduling

import urokplan.courses.application.CoursesApi
import urokplan.database.TransactionRunner
import urokplan.identity.application.IdentityApi
import urokplan.relationships.application.RelationshipsApi
import urokplan.scheduling.application.SchedulingApi
import urokplan.scheduling.application.SchedulingService
import urokplan.scheduling.application.SchedulingGroupScheduleUpdater
import urokplan.scheduling.application.AttendanceBillingUpdater
import urokplan.scheduling.application.SchedulingUseCases
import urokplan.scheduling.infrastructure.ExposedSchedulingRepository
import java.time.Clock

class SchedulingModule private constructor(
    val useCases: SchedulingUseCases,
    val api: SchedulingApi,
) {
    companion object {
        fun createGroupScheduleUpdater(
            transactions: TransactionRunner,
            clock: Clock,
        ) = SchedulingGroupScheduleUpdater(transactions, ExposedSchedulingRepository(), clock)

        fun create(
            transactions: TransactionRunner,
            identity: IdentityApi,
            relationships: RelationshipsApi,
            courses: CoursesApi,
            billing: AttendanceBillingUpdater,
            clock: Clock,
        ): SchedulingModule {
            val service = SchedulingService(
                transactions,
                ExposedSchedulingRepository(),
                identity,
                relationships,
                courses,
                billing,
                clock,
            )
            return SchedulingModule(useCases = service, api = service)
        }
    }
}
