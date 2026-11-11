package urokplan.learning.application

data class AssessmentSummaryView(
    val studentPublicId: String,
    val averageScore: Double?,
    val assessments: List<AssessmentView>,
)
