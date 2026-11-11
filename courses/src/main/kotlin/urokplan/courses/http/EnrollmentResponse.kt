package urokplan.courses.http

import kotlinx.serialization.Serializable

@Serializable
data class EnrollmentResponse(
    val courseId: String,
    val student: CourseAccountResponse,
    val priceMinor: Long,
    val status: String,
    val enrolledAt: String,
    val endedAt: String?,
)
