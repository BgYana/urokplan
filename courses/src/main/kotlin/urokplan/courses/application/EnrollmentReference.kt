package urokplan.courses.application

import java.util.UUID

data class EnrollmentReference(
    val courseId: UUID,
    val studentId: UUID,
    val priceMinor: Long,
)
