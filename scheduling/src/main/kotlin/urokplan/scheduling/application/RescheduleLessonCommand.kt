package urokplan.scheduling.application

import urokplan.scheduling.domain.ChangeScope
import java.time.Instant

data class RescheduleLessonCommand(
    val startsAt: Instant,
    val durationMinutes: Int,
    val scope: ChangeScope,
)
