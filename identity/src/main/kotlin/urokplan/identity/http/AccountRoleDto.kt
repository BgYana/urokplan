package urokplan.identity.http

import kotlinx.serialization.Serializable

@Serializable
enum class AccountRoleDto {
    STUDENT,
    TEACHER,
    PARENT,
}
