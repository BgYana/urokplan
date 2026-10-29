package urokplan.learning.application

import urokplan.learning.domain.Assessment
import urokplan.learning.domain.Assignment
import urokplan.learning.domain.LessonFeedback
import urokplan.learning.domain.SubmissionAttempt
import java.util.UUID

interface LearningRepository {
    fun createAssignment(assignment: Assignment): Assignment
    fun findAssignment(id: UUID): Assignment?
    fun listAssignments(lessonId: UUID?): List<Assignment>

    fun createSubmission(submission: SubmissionAttempt): SubmissionAttempt
    fun updateSubmission(submission: SubmissionAttempt): SubmissionAttempt
    fun findSubmission(id: UUID): SubmissionAttempt?
    fun listSubmissions(assignmentId: UUID, studentIds: Set<UUID>? = null): List<SubmissionAttempt>
    fun findLatestSubmission(assignmentId: UUID, studentId: UUID): SubmissionAttempt?

    fun createAssessment(assessment: Assessment): Assessment
    fun listAssessments(studentId: UUID, courseId: UUID, lessonId: UUID? = null): List<Assessment>

    fun upsertFeedback(feedback: LessonFeedback): LessonFeedback
    fun listFeedback(lessonId: UUID, studentIds: Set<UUID>? = null): List<LessonFeedback>
}
