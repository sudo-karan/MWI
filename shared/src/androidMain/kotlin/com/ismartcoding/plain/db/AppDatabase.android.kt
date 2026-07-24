package com.ismartcoding.plain.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ismartcoding.plain.platform.AndroidApp
import kotlinx.coroutines.Dispatchers

actual fun buildAppDatabase(): AppDatabase {
    val ctx = AndroidApp.context
    val dbFile = ctx.getDatabasePath("mwi.db")
    return Room.databaseBuilder<AppDatabase>(
        context = ctx,
        name = dbFile.absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
}
