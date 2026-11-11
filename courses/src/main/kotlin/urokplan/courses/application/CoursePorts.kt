package urokplan.courses.application

import java.util.UUID

class DuplicateActiveMembershipException : RuntimeException()

interface GroupScheduleUpdater {
    fun addStudentToFutureLessons(groupId: UUID, studentId: UUID, priceMinor: Long)
    fun removeStudentFromFutureLessons(groupId: UUID, studentId: UUID)
    fun removeStudentFromCourseFutureGroupLessons(courseId: UUID, studentId: UUID)
}

class GroupScheduleConflictException : RuntimeException()
