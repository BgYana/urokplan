package urokplan.learning.application

data class ReviewSubmissionCommand(
    val decision: ReviewDecision,
    val comment: String,
    val score: Int,
)
