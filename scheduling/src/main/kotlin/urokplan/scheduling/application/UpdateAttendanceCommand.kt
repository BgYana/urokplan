package urokplan.scheduling.application

import urokplan.scheduling.domain.AttendanceStatus

data class UpdateAttendanceCommand(
    val status: AttendanceStatus,
    val reason: String?,
)
