package urokplan.scheduling.application

import urokplan.scheduling.domain.AttendanceStatus
import java.util.UUID

data class ParticipantReference(
    val studentId: UUID,
    val priceMinor: Long,
    val attendance: AttendanceStatus,
)
