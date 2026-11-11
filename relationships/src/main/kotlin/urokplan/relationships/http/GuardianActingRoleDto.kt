package urokplan.relationships.http

import kotlinx.serialization.Serializable

@Serializable
enum class GuardianActingRoleDto {
    PARENT,
    STUDENT,
}
