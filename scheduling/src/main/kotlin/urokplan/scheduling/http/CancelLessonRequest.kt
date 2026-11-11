package urokplan.scheduling.http

import kotlinx.serialization.Serializable

@Serializable
data class CancelLessonRequest(val scope: String)
