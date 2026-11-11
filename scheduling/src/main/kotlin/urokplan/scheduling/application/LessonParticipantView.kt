package urokplan.scheduling.application

import urokplan.identity.application.AccountReference
import urokplan.scheduling.domain.AttendanceStatus

data class LessonParticipantView(
    val student: AccountReference,
    val priceMinor: Long,
    val attendance: AttendanceStatus,
)
