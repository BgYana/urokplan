package urokplan.identity.application

import urokplan.identity.domain.AccountRole

data class PublicAccountProfile(
    val publicId: String,
    val displayName: String,
    val roles: Set<AccountRole>,
)
