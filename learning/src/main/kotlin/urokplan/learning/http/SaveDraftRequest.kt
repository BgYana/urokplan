package urokplan.learning.http

import kotlinx.serialization.Serializable

@Serializable
data class SaveDraftRequest(val text: String? = null, val links: List<String> = emptyList())
