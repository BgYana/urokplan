package urokplan.learning

import urokplan.courses.application.CoursesApi
import urokplan.database.TransactionRunner
import urokplan.identity.application.IdentityApi
import urokplan.learning.application.LearningService
import urokplan.learning.application.LearningUseCases
import urokplan.learning.infrastructure.ExposedLearningRepository
import urokplan.relationships.application.RelationshipsApi
import urokplan.scheduling.application.SchedulingApi
import java.time.Clock

class LearningModule private constructor(
    val useCases: LearningUseCases,
) {
    companion object {
        fun create(
            transactions: TransactionRunner,
            identity: IdentityApi,
            relationships: RelationshipsApi,
            courses: CoursesApi,
            scheduling: SchedulingApi,
            clock: Clock,
        ): LearningModule = LearningModule(
            LearningService(
                transactions,
                ExposedLearningRepository(),
                identity,
                relationships,
                courses,
                scheduling,
                clock,
            ),
        )
    }
}
