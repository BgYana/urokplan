package urokplan.relationships.application

import urokplan.identity.application.AccountReference
import urokplan.identity.domain.AccountRole

internal fun AccountReference.requireRole(role: AccountRole, code: String) {
    if (role !in roles) throw RelationshipException(code, RelationshipErrorKind.BAD_REQUEST)
}
