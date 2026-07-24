package com.ismartcoding.plain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Platform-provided preferences DataStore (file location differs per platform). */
expect fun createPreferencesDataStore(): DataStore<Preferences>

/**
 * Typed accessors over the app's DataStore (spec §10/§11). Holds only what must survive a
 * restart: the login password hash, the rotating URL token seed, the keystore password, the 2FA
 * flag (default ON), notification filters, and the URL-token-rotation flag. Never stores the
 * plaintext login password.
 */
object AppPreferences {
    private val store: DataStore<Preferences> by lazy { createPreferencesDataStore() }

    private val PASSWORD_HASH = stringPreferencesKey("password_hash")
    private val LOGIN_PASSWORD = stringPreferencesKey("login_password")
    private val URL_TOKEN = stringPreferencesKey("url_token")
    private val KEYSTORE_PASSWORD = stringPreferencesKey("keystore_password")
    private val TWO_FACTOR = booleanPreferencesKey("two_factor_enabled")
    private val URL_TOKEN_ROTATION = booleanPreferencesKey("url_token_rotation_enabled")
    private val NOTIFICATION_FILTER = stringPreferencesKey("notification_filter")
    private val DEVICE_NAME = stringPreferencesKey("device_name")
    private val WEB_ENABLED = booleanPreferencesKey("web_enabled")

    suspend fun getPasswordHash(): String? = store.data.map { it[PASSWORD_HASH] }.first()
    suspend fun setPasswordHash(value: String) = edit { it[PASSWORD_HASH] = value }

    /**
     * The web-console login password. It is a machine-generated, high-entropy secret shown on the
     * device (never a user-chosen/reused one), stored locally on a device with `allowBackup=false`.
     * The plaintext is required to derive the WS handshake key (SHA-512(password)[..32], spec §5).
     */
    suspend fun getLoginPassword(): String? = store.data.map { it[LOGIN_PASSWORD] }.first()
    suspend fun setLoginPassword(value: String) = edit { it[LOGIN_PASSWORD] = value }

    suspend fun getUrlToken(): String? = store.data.map { it[URL_TOKEN] }.first()
    suspend fun setUrlToken(value: String) = edit { it[URL_TOKEN] = value }

    suspend fun getKeystorePassword(): String? = store.data.map { it[KEYSTORE_PASSWORD] }.first()
    suspend fun setKeystorePassword(value: String) = edit { it[KEYSTORE_PASSWORD] = value }

    /** Two-factor (on-device approval) defaults to ON per spec §5. */
    val twoFactorEnabled: Flow<Boolean> = store.data.map { it[TWO_FACTOR] ?: true }
    suspend fun isTwoFactorEnabled(): Boolean = twoFactorEnabled.first()
    suspend fun setTwoFactorEnabled(value: Boolean) = edit { it[TWO_FACTOR] = value }

    suspend fun isUrlTokenRotationEnabled(): Boolean =
        store.data.map { it[URL_TOKEN_ROTATION] ?: true }.first()
    suspend fun setUrlTokenRotationEnabled(value: Boolean) = edit { it[URL_TOKEN_ROTATION] = value }

    suspend fun getNotificationFilter(): String? = store.data.map { it[NOTIFICATION_FILTER] }.first()
    suspend fun setNotificationFilter(value: String) = edit { it[NOTIFICATION_FILTER] = value }

    val deviceName: Flow<String> = store.data.map { it[DEVICE_NAME] ?: "" }
    suspend fun getDeviceName(): String = deviceName.first()
    suspend fun setDeviceName(value: String) = edit { it[DEVICE_NAME] = value }

    val webEnabled: Flow<Boolean> = store.data.map { it[WEB_ENABLED] ?: false }
    suspend fun isWebEnabled(): Boolean = webEnabled.first()
    suspend fun setWebEnabled(value: Boolean) = edit { it[WEB_ENABLED] = value }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }
}
