package urokplan.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager

object DatabaseFactory {
    private const val DEFAULT_CHANGELOG = "db/changelog/db.changelog-master.yaml"

    fun start(
        config: DatabaseConfig,
        changelog: String = DEFAULT_CHANGELOG,
    ): DatabaseHandle {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                maximumPoolSize = config.maximumPoolSize
                minimumIdle = 1
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                validate()
            },
        )

        try {
            runMigrations(dataSource, changelog)
            val database = Database.connect(dataSource)
            return DatabaseHandle(dataSource, database)
        } catch (exception: Exception) {
            dataSource.close()
            throw exception
        }
    }

    private fun runMigrations(dataSource: HikariDataSource, changelog: String) {
        dataSource.connection.use { connection ->
            Liquibase(
                changelog,
                ClassLoaderResourceAccessor(),
                JdbcConnection(connection),
            ).use { liquibase ->
                liquibase.update("")
            }
        }
    }
}

class DatabaseHandle internal constructor(
    private val dataSource: HikariDataSource,
    val database: Database,
) : TransactionRunner, AutoCloseable {
    fun isReady(): Boolean = runCatching {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT 1").use { statement ->
                statement.executeQuery().use { result -> result.next() && result.getInt(1) == 1 }
            }
        }
    }.getOrDefault(false)

    fun <T> transaction(block: org.jetbrains.exposed.sql.Transaction.() -> T): T =
        org.jetbrains.exposed.sql.transactions.transaction(database, statement = block)

    override fun <T> inTransaction(block: () -> T): T =
        org.jetbrains.exposed.sql.transactions.transaction(database) { block() }

    override fun close() {
        TransactionManager.closeAndUnregister(database)
        dataSource.close()
    }
}

interface TransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}
