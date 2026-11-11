package urokplan.courses.application

import java.util.UUID

data class CreateGroupCommand(
    val courseId: UUID,
    val name: String,
    val callLink: String?,
)
