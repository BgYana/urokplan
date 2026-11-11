package urokplan.scheduling.domain

enum class LessonStatus {
    SCHEDULED,
    CANCELLED,
}

enum class SeriesStatus {
    ACTIVE,
    CANCELLED,
}

enum class AttendanceStatus {
    UNKNOWN,
    PRESENT,
    EXCUSED_ABSENCE,
    UNEXCUSED_ABSENCE,
}

enum class ChangeScope {
    THIS,
    THIS_AND_FOLLOWING,
}
