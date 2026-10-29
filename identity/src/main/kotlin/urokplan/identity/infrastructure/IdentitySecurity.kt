package urokplan.identity.infrastructure

import de.mkammerer.argon2.Argon2Factory
import urokplan.identity.application.PasswordHasher
import urokplan.identity.application.SessionTokenGenerator
import urokplan.identity.application.SessionTokenHasher
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

internal class Argon2PasswordHasher : PasswordHasher {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    override fun hash(password: String): String = withCharacters(password) { characters ->
        argon2.hash(2, 19 * 1024, 1, characters)
    }

    override fun verify(password: String, hash: String): Boolean = withCharacters(password) { characters ->
        argon2.verify(hash, characters)
    }

    private fun <T> withCharacters(password: String, block: (CharArray) -> T): T {
        val characters = password.toCharArray()
        return try {
            block(characters)
        } finally {
            argon2.wipeArray(characters)
        }
    }
}

internal class SecureSessionTokenGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
) : SessionTokenGenerator {
    override fun generate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

internal class Sha256SessionTokenHasher : SessionTokenHasher {
    override fun hash(token: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)),
    )
}
