package com.arjun.mooary.data.local

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.arjun.mooary.core.platform.AndroidPlatform
import com.arjun.mooary.data.local.db.DiaryDatabase

/**
 * Android driver over the framework SQLite.
 *
 * The schema is handed to the driver rather than created by hand: `AndroidSqliteDriver`
 * runs it inside the helper's `onCreate`, which only fires the first time the file exists.
 * Calling `Schema.create` on every launch — as a `Driver` implementation invites — would
 * fail with "table already exists" on the second run.
 *
 * The file lives in the app-private databases directory, and the manifest sets
 * `allowBackup="false"`, so journal text is never copied into a cloud backup.
 *
 * The connection settings are the same as the other platforms, so a query means the same
 * thing everywhere, but the two pragmas cannot be applied the same way on both paths.
 *
 * `driver.execute` routes to `SQLiteDatabase.execSQL`, which refuses any statement that
 * returns rows. `PRAGMA foreign_keys = ON` returns none and goes through fine;
 * `PRAGMA journal_mode = WAL` returns one row ("wal") and throws "Queries can be performed
 * using SQLiteDatabase query or rawQuery methods only" — which is why the WAL line below
 * does not look like the one-liner in the iOS and desktop factories. That string is correct
 * SQL and it compiles; it only fails on a device.
 */
actual fun createDatabaseDriver(): SqlDriver {
    val context: Context = AndroidPlatform.requireContext().applicationContext

    val driver = AndroidSqliteDriver(
        schema = DiaryDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
        useNoBackupDirectory = true,
    )

    driver.execute(null, "PRAGMA foreign_keys = ON", 0) {}
    driver.executeQuery(
        identifier = null,
        sql = "PRAGMA journal_mode = WAL",
        mapper = { cursor ->
            // The resulting mode is written into the database header, so this row only has
            // to be consumed, not read.
            cursor.next().value
            QueryResult.Value(Unit)
        },
        parameters = 0,
    )
    return driver
}

actual fun databaseLocationDescription(): String =
    AndroidPlatform.requireContext().applicationContext
        .getDatabasePath(DATABASE_NAME).absolutePath

private const val DATABASE_NAME = "diary.db"
