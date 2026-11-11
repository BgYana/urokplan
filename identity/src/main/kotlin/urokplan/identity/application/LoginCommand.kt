package urokplan.identity.application

data class LoginCommand(
    val login: String,
    val password: String,
)
