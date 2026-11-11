package urokplan.courses.application

data class UpdateGroupCommand(
    val name: String,
    val callLink: String?,
)
