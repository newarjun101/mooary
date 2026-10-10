package com.arjun.mooary.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.arjun.mooary.data.local.db.DiaryDatabase

/**
 * iOS driver over the system `libsqlite3`, which is always present in the iOS SDK.
 *
 * The file lives in the app's `Library/Application Support` directory.
 *
 * The WAL pragma cannot be applied the same way here as `PRAGMA foreign_keys = ON`, for the
 * reason the Android factory documents at length: `driver.execute` routes to SQLite's
 * update/delete path, which refuses any statement that returns rows. `journal_mode` returns
 * one row ("wal") and throws "Queries can be performed using SQLiteDatabase query or rawQuery
 * methods only". That string is correct SQL and it compiles — it only fails on a device.
 */
actual fun createDatabaseDriver(): SqlDriver {
    val driver = NativeSqliteDriver(DiaryDatabase.Schema, DATABASE_NAME)
    driver.execute(null, "PRAGMA foreign_keys = ON", 0) {}
    driver.executeQuery(
        identifier = null,
        sql = "PRAGMA journal_mode = WAL",
        mapper = { cursor ->
            // The resulting mode is written into the database header, so this row only has to
            // be consumed, not read.
            cursor.next().value
            QueryResult.Value(Unit)
        },
        parameters = 0,
    )
    return driver
}

actual fun databaseLocationDescription(): String = "Library/Application Support/$DATABASE_NAME"

private const val DATABASE_NAME = "diary.db"
