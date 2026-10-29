package urokplan.identity.http

import kotlinx.serialization.Serializable
import urokplan.identity.application.LoginCommand
import urokplan.identity.application.LoginResult
import urokplan.identity.application.PublicAccountProfile
import urokplan.identity.application.RegisterAccountCommand
import urokplan.identity.domain.Account
import urokplan.identity.domain.AccountRole

@Serializable
enum class AccountRoleDto {
    STUDENT,
    TEACHER,
    PARENT,
}

@Serializable
data class RegisterAccountRequest(
    val login: String,
    val displayName: String,
    val password: String,
    val roles: Set<AccountRoleDto>,
    val billingTimeZone: String = "UTC",
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
)

@Serializable
data class AccountResponse(
    val publicId: String,
    val login: String,
    val displayName: String,
    val roles: List<AccountRoleDto>,
    val billingTimeZone: String,
)

@Serializable
data class UpdateBillingTimeZoneRequest(val billingTimeZone: String)

@Serializable
data class PublicAccountResponse(
    val publicId: String,
    val displayName: String,
    val roles: List<AccountRoleDto>,
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: String,
    val account: AccountResponse,
)

@Serializable
data class IdentityErrorResponse(val code: String)

internal fun RegisterAccountRequest.toCommand() = RegisterAccountCommand(
    login = login,
    displayName = displayName,
    password = password,
    roles = roles.mapTo(linkedSetOf()) { AccountRole.valueOf(it.name) },
    billingTimeZone = billingTimeZone,
)

internal fun LoginRequest.toCommand() = LoginCommand(login, password)

internal fun Account.toResponse() = AccountResponse(
    publicId = publicId,
    login = login,
    displayName = displayName,
    roles = roles.sortedBy(AccountRole::name).map { AccountRoleDto.valueOf(it.name) },
    billingTimeZone = billingTimeZone,
)

internal fun PublicAccountProfile.toResponse() = PublicAccountResponse(
    publicId = publicId,
    displayName = displayName,
    roles = roles.sortedBy(AccountRole::name).map { AccountRoleDto.valueOf(it.name) },
)

internal fun LoginResult.toResponse() = LoginResponse(
    accessToken = accessToken,
    tokenType = "Bearer",
    expiresAt = expiresAt.toString(),
    account = account.toResponse(),
)
