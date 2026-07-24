package com.ismartcoding.plain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ismartcoding.plain.platform.AndroidApp

actual fun createPreferencesDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        produceFile = { AndroidApp.context.preferencesDataStoreFile("mwi_prefs") },
    )
