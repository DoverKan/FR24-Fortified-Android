package com.skydronex.fr24fortified.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

class ConfigRepository(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        private val KEY_IP = stringPreferencesKey("ip_address")
        private val KEY_DEVICE_TYPE = stringPreferencesKey("device_type")
        private val KEY_CONSOLE_PORT = intPreferencesKey("console_port")
        private val KEY_FEEDER_PORT = intPreferencesKey("feeder_port")
        private val KEY_MAPBOX_TOKEN = stringPreferencesKey("mapbox_token")
        private val KEY_CONFIGURED           = booleanPreferencesKey("is_configured")
        private val KEY_LAST_SEEN_VERSION     = intPreferencesKey("last_seen_version")
    }

    val appState: Flow<AppState> = dataStore.data.map { prefs ->
        if (prefs[KEY_CONFIGURED] != true) return@map AppState.NeedsSetup
        val ip = prefs[KEY_IP] ?: return@map AppState.NeedsSetup
        val typeStr = prefs[KEY_DEVICE_TYPE] ?: return@map AppState.NeedsSetup
        val consolePort = prefs[KEY_CONSOLE_PORT] ?: 30003
        val feederPort = prefs[KEY_FEEDER_PORT] ?: 8754
        val mapboxToken = prefs[KEY_MAPBOX_TOKEN] ?: ""
        AppState.Ready(AppConfig(ip, DeviceType.valueOf(typeStr), consolePort, feederPort, mapboxToken))
    }

    val lastSeenVersionCode: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SEEN_VERSION] ?: 0
    }

    suspend fun saveLastSeenVersionCode(code: Int) {
        dataStore.edit { prefs -> prefs[KEY_LAST_SEEN_VERSION] = code }
    }

    suspend fun saveConfig(config: AppConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_IP] = config.ipAddress
            prefs[KEY_DEVICE_TYPE] = config.deviceType.name
            prefs[KEY_CONSOLE_PORT] = config.consolePort
            prefs[KEY_FEEDER_PORT] = config.feederPort
            prefs[KEY_MAPBOX_TOKEN] = config.mapboxToken
            prefs[KEY_CONFIGURED] = true
        }
    }
}
