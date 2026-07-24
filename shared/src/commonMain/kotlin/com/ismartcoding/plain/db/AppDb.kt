package com.ismartcoding.plain.db

/** Lazily-built process-wide database handle. First access must be after the app is initialized. */
object AppDb {
    val instance: AppDatabase by lazy { buildAppDatabase() }
}
