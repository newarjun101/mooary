package com.arjun.soul_note.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arjun.soul_note.data.local.db.DiaryDatabase
import java.io.File
import java.sql.Connection

/**
 * Desktop driver: the bundled JVM SQLite build, with a file in the user's data directory.
 *
 * `foreign_keys` is enforced so a dangling reference can never be written, and WAL keeps
 * the UI's reads non-blocking while the sync manager writes.
 */
actual fun createDatabaseDriver(): SqlDriver {
    val directory = databaseDirectory()
    if (!directory.exists()) directory.mkdirs()
    val file = File(directory, "diary.db")

    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    val connection = driver.getConnection()
    connection.createStatement().use { statement ->
        statement.execute("PRAGMA foreign_keys = ON")
        statement.execute("PRAGMA journal_mode = WAL")
    }
    // The generated schema issues bare `CREATE TABLE`, so calling it against a populated file
    // throws "table diary_entries already exists". That made the desktop app start exactly once
    // per database file: the first launch created the tables, and every launch after it died
    // inside Koin before the first frame. Reconciling the version here is what makes a second
    // launch work.
    applySchema(driver, connection)
    return driver
}

private fun applySchema(driver: SqlDriver, connection: Connection) {
    val schema = DiaryDatabase.Schema
    val recorded = connection.userVersion()
    when {
        // No tables and no recorded version: a genuinely new file, so build the schema.
        recorded == 0L && !connection.hasSchema() -> {
            schema.create(driver).value
            connection.setUserVersion(schema.version)
        }
        // Tables present but unversioned. An older build created them from these same `.sq`
        // files without stamping a version, so the schema is already the current one and the
        // only thing missing is the stamp.
        recorded == 0L -> connection.setUserVersion(schema.version)
        // A real older schema, so migrate it forward.
        recorded < schema.version -> schema.migrate(driver, recorded, schema.version).value
    }
}

private fun Connection.userVersion(): Long = createStatement().use { statement ->
    statement.executeQuery("PRAGMA user_version").use { rows ->
        if (rows.next()) rows.getLong(1) else 0L
    }
}

private fun Connection.setUserVersion(version: Long) {
    createStatement().use { it.execute("PRAGMA user_version = $version") }
}

private fun Connection.hasSchema(): Boolean = createStatement().use { statement ->
    statement.executeQuery(
        "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'diary_entries'",
    ).use { rows -> rows.next() && rows.getInt(1) > 0 }
}

actual fun databaseLocationDescription(): String = databaseDirectory().absolutePath

private fun databaseDirectory(): File {
    val home = System.getProperty("user.home") ?: "."
    val override = System.getenv("DIARY_DATA_DIR")
    return if (!override.isNullOrBlank()) File(override) else File(home, ".diary-app")
}
