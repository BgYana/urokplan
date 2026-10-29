package urokplan.courses.domain

import java.time.Instant
import java.util.UUID

enum class CourseFormat {
    INDIVIDUAL,
    GROUP,
}

enum class ParticipationStatus {
    ACTIVE,
    INACTIVE,
}

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

data class Enrollment(
    val courseId: UUID,
    val studentAccountId: UUID,
    val priceMinor: Long,
    val status: ParticipationStatus,
    val enrolledAt: Instant,
    val endedAt: Instant? = null,
)

data class StudyGroup(
    val id: UUID,
    val courseId: UUID,
    val name: String,
    val callLink: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class GroupMembership(
    val id: UUID,
    val groupId: UUID,
    val courseId: UUID,
    val studentAccountId: UUID,
    val status: ParticipationStatus,
    val joinedAt: Instant,
    val leftAt: Instant? = null,
)
