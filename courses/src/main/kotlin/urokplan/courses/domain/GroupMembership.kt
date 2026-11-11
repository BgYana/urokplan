package urokplan.courses.domain

import java.time.Instant
import java.util.UUID

data class GroupMembership(
    val id: UUID,
    val groupId: UUID,
    val courseId: UUID,
    val studentAccountId: UUID,
    val status: ParticipationStatus,
    val joinedAt: Instant,
    val leftAt: Instant? = null,
)
