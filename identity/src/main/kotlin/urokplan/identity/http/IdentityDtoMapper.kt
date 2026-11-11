package urokplan.identity.http

import urokplan.identity.application.LoginCommand
import urokplan.identity.application.LoginResult
import urokplan.identity.application.PublicAccountProfile
import urokplan.identity.application.RegisterAccountCommand
import urokplan.identity.domain.Account
import urokplan.identity.domain.AccountRole

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
