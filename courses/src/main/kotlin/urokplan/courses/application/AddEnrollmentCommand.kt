package urokplan.courses.application

data class AddEnrollmentCommand(
    val studentPublicId: String,
    val priceMinor: Long,
)
