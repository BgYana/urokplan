package urokplan.identity.application

import urokplan.identity.domain.AccountRole
import java.util.UUID

data class AccountReference(
    val id: UUID,
    val publicId: String,
    val displayName: String,
    val roles: Set<AccountRole>,
    val billingTimeZone: String = "UTC",
)
