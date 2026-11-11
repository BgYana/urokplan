package urokplan.scheduling.application

data class ParticipantPriceCommand(
    val studentPublicId: String,
    val priceMinor: Long? = null,
)
