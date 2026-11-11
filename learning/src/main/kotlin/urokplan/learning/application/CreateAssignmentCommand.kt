package urokplan.learning.application

import java.time.Instant
import java.util.UUID

data class CreateAssignmentCommand(
    val lessonId: UUID,
    val text: String,
    val links: List<String>,
    val deadline: Instant?,
    val targetStudentPublicIds: Set<String>,
)
