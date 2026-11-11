package urokplan.courses.application

import urokplan.identity.application.AccountReference
import java.time.Instant
import java.util.UUID

data class GroupMemberView(
    val membershipId: UUID,
    val student: AccountReference,
    val joinedAt: Instant,
)
