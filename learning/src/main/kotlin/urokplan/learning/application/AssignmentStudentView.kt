package urokplan.learning.application

data class AssignmentStudentView(
    val studentPublicId: String,
    val studentDisplayName: String,
    val submissions: List<SubmissionView>,
)
