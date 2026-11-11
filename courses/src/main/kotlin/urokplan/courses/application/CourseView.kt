package urokplan.courses.application

import urokplan.courses.domain.CourseFormat
import urokplan.identity.application.AccountReference
import java.time.Instant
import java.util.UUID

data class CourseView(
    val id: UUID,
    val teacher: AccountReference,
    val title: String,
    val subject: String,
    val format: CourseFormat,
    val callLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
