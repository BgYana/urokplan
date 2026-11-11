package urokplan.learning.application

import java.time.Instant
import java.util.UUID

data class AssignmentView(
    val id: UUID,
    val lessonId: UUID,
    val text: String,
    val links: List<String>,
    val deadline: Instant?,
    val overdue: Boolean,
    val createdAt: Instant,
    val students: List<AssignmentStudentView>,
)
