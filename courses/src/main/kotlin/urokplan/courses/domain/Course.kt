package urokplan.courses.domain

import java.time.Instant
import java.util.UUID

data class Course(
    val id: UUID,
    val teacherAccountId: UUID,
    val title: String,
    val subject: String,
    val format: CourseFormat,
    val callLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
