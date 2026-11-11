package urokplan.courses.application

import urokplan.courses.domain.CourseFormat
import java.util.UUID

data class CourseReference(
    val id: UUID,
    val teacherId: UUID,
    val format: CourseFormat,
    val title: String,
    val callLink: String?,
)
