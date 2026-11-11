package urokplan.database

import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseFactoryTest {
    @Test
    fun `liquibase initializes a clean postgres database`() {
        PostgreSQLContainer("postgres:17-alpine").use { postgres ->
            postgres.start()

            DatabaseFactory.start(
                DatabaseConfig(
                    jdbcUrl = postgres.jdbcUrl,
                    username = postgres.username,
                    password = postgres.password,
                    maximumPoolSize = 2,
                ),
                changelog = "db/changelog/database-test-master.yaml",
            ).use { database ->
                assertTrue(database.isReady())
                val appliedChanges = database.transaction {
                    exec("SELECT COUNT(*) FROM databasechangelog") { result ->
                        result.next()
                        result.getInt(1)
                    }
                }
                assertEquals(1, appliedChanges)
            }
        }
    }
}
