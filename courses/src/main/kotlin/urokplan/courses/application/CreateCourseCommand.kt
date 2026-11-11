package urokplan.courses.application

import urokplan.courses.domain.CourseFormat

data class CreateCourseCommand(
    val title: String,
    val subject: String,
    val format: CourseFormat,
    val callLink: String?,
)
