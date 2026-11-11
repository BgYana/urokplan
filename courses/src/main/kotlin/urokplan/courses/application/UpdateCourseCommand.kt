package urokplan.courses.application

data class UpdateCourseCommand(
    val title: String,
    val subject: String,
    val callLink: String?,
)
