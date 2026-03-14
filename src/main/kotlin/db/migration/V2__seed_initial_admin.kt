package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.sql.Types
import java.util.Locale

class V2__seed_initial_admin : BaseJavaMigration() {
    private val passwordEncoder = BCryptPasswordEncoder()

    override fun migrate(context: Context) {
        val adminEmail = requiredValue("ADMIN_EMAIL").trim().lowercase(Locale.ROOT)
        val adminPassword = requiredValue("ADMIN_PASSWORD")
        val adminName = requiredValue("ADMIN_NAME").trim()

        context.connection.prepareStatement("select id from users where email = ?").use { statement ->
            statement.setString(1, adminEmail)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    return
                }
            }
        }

        context.connection
            .prepareStatement(
                """
                insert into users (role, name, email, password_hash, phone_number, postal_code)
                values (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, "ADMIN")
                statement.setString(2, adminName)
                statement.setString(3, adminEmail)
                statement.setString(4, passwordEncoder.encode(adminPassword))
                statement.setNull(5, Types.VARCHAR)
                statement.setNull(6, Types.VARCHAR)
                statement.executeUpdate()
            }
    }

    private fun requiredValue(key: String): String =
        System
            .getenv(key)
            ?.takeIf { it.isNotBlank() }
            ?: System.getProperty(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("$key must be configured for admin bootstrap")
}
