package com.arjun.mooary.data.local

import app.cash.sqldelight.db.SqlDriver
import com.arjun.mooary.data.local.db.DiaryDatabase

/**
 * Creates the platform's SQLite driver.
 *
 * The driver is the one genuinely non-portable dependency: Android ships the framework
 * SQLite, desktop the bundled JVM driver, iOS the system `libsqlite3`. Everything above
 * this seam is common code, and each implementation applies the same pragmas so that
 * query plans and performance are identical everywhere.
 */
expect fun createDatabaseDriver(): SqlDriver

/** Human-readable description of where the journal is stored, for the privacy screen. */
expect fun databaseLocationDescription(): String

/** The database, built on whichever driver the platform provided. */
fun createDiaryDatabase(): DiaryDatabase = DiaryDatabase(createDatabaseDriver())
