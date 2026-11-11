package urokplan.courses.application

import urokplan.courses.domain.ParticipationStatus
import urokplan.identity.application.AccountReference
import java.time.Instant
import java.util.UUID

data class EnrollmentView(
    val courseId: UUID,
    val student: AccountReference,
    val priceMinor: Long,
    val status: ParticipationStatus,
    val enrolledAt: Instant,
    val endedAt: Instant?,
)
