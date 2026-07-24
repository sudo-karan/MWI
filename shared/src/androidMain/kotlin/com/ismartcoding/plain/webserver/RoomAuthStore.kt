package com.ismartcoding.plain.webserver

import com.ismartcoding.plain.crypto.AuthTokens
import com.ismartcoding.plain.db.AppDatabase
import com.ismartcoding.plain.db.DSession
import com.ismartcoding.plain.preferences.AppPreferences
import com.ismartcoding.plain.web.auth.AuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * [AuthStore] backed by Room (`DSession`) + DataStore (the login password). The store's synchronous
 * interface bridges to the suspend DAO/prefs via `runBlocking(IO)`; call volume is a handful of
 * logins, so the bridge cost is negligible.
 */
class RoomAuthStore(private val db: AppDatabase) : AuthStore {

    override fun passwordHash(): String? = runBlocking(Dispatchers.IO) {
        AppPreferences.getLoginPassword()?.let { AuthTokens.passwordHash(it) }
    }

    override fun saveSession(session: DSession) = runBlocking(Dispatchers.IO) {
        db.sessionDao().upsert(session)
    }

    override fun sessionByToken(token: String): DSession? = runBlocking(Dispatchers.IO) {
        db.sessionDao().getByToken(token)
    }
}
