package urokplan.identity.application

import urokplan.identity.domain.Account
import urokplan.identity.domain.AccountCredentials
import urokplan.identity.domain.NewAccount
import java.util.UUID

interface AccountRepository {
    fun findCredentialsByLogin(login: String): AccountCredentials?
    fun findById(id: UUID): Account?
    fun findByIds(ids: Set<UUID>): List<Account>
    fun findByPublicId(publicId: String): Account?
    fun create(account: NewAccount): Account
    fun updateBillingTimeZone(accountId: UUID, billingTimeZone: String): Account
}
