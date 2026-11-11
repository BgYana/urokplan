package urokplan.courses.application

import java.util.UUID

data class GroupReference(
    val id: UUID,
    val courseId: UUID,
    val callLink: String?,
    val studentIds: Set<UUID>,
)
