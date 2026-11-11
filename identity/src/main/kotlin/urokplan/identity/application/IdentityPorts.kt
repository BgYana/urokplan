package urokplan.identity.application

interface PasswordHasher {
    fun hash(password: String): String
    fun verify(password: String, hash: String): Boolean
}

interface SessionTokenGenerator {
    fun generate(): String
}

interface SessionTokenHasher {
    fun hash(token: String): String
}

class DuplicateAccountException : RuntimeException()
