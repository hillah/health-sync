package com.example.healthsync.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.healthsync.data.model.SyncLogItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore by preferencesDataStore(name = "health_sync_settings")

data class AppSettings(
    val webhookUrl: String = "",
    val customHeaderName: String = "Authorization",
    val customHeaderValue: String = "",
    val syncIntervalHours: Long = 6L,
    val isAutoSyncEnabled: Boolean = false,
    val includeDetailedRecords: Boolean = true,
    val lastSyncSuccessTime: String? = null,
    val syncLogs: List<SyncLogItem> = emptyList()
)

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val KEY_WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val KEY_HEADER_NAME = stringPreferencesKey("header_name")
        val KEY_HEADER_VALUE = stringPreferencesKey("header_value")
        val KEY_INTERVAL_HOURS = longPreferencesKey("sync_interval_hours")
        val KEY_AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val KEY_INCLUDE_DETAILED = booleanPreferencesKey("include_detailed_records")
        val KEY_LAST_SYNC_SUCCESS = stringPreferencesKey("last_sync_success_time")
        val KEY_SYNC_LOGS_JSON = stringPreferencesKey("sync_logs_json")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        val logsJson = pref[KEY_SYNC_LOGS_JSON] ?: "[]"
        val logs = try {
            json.decodeFromString<List<SyncLogItem>>(logsJson)
        } catch (e: Exception) {
            emptyList()
        }

        AppSettings(
            webhookUrl = pref[KEY_WEBHOOK_URL] ?: "",
            customHeaderName = pref[KEY_HEADER_NAME] ?: "Authorization",
            customHeaderValue = pref[KEY_HEADER_VALUE] ?: "",
            syncIntervalHours = pref[KEY_INTERVAL_HOURS] ?: 6L,
            isAutoSyncEnabled = pref[KEY_AUTO_SYNC_ENABLED] ?: false,
            includeDetailedRecords = pref[KEY_INCLUDE_DETAILED] ?: true,
            lastSyncSuccessTime = pref[KEY_LAST_SYNC_SUCCESS],
            syncLogs = logs
        )
    }

    suspend fun updateWebhookSettings(
        url: String,
        headerName: String,
        headerValue: String
    ) {
        context.dataStore.edit { pref ->
            pref[KEY_WEBHOOK_URL] = url
            pref[KEY_HEADER_NAME] = headerName
            pref[KEY_HEADER_VALUE] = headerValue
        }
    }

    suspend fun updateSyncScheduleSettings(
        intervalHours: Long,
        isAutoSyncEnabled: Boolean,
        includeDetailed: Boolean
    ) {
        context.dataStore.edit { pref ->
            pref[KEY_INTERVAL_HOURS] = intervalHours
            pref[KEY_AUTO_SYNC_ENABLED] = isAutoSyncEnabled
            pref[KEY_INCLUDE_DETAILED] = includeDetailed
        }
    }

    suspend fun addSyncLog(log: SyncLogItem) {
        context.dataStore.edit { pref ->
            val logsJson = pref[KEY_SYNC_LOGS_JSON] ?: "[]"
            val currentLogs = try {
                json.decodeFromString<List<SyncLogItem>>(logsJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }

            // 直近20件のみ保持
            currentLogs.add(0, log)
            val trimmed = currentLogs.take(20)
            pref[KEY_SYNC_LOGS_JSON] = json.encodeToString(trimmed)

            if (log.isSuccess) {
                pref[KEY_LAST_SYNC_SUCCESS] = log.timestamp
            }
        }
    }

    suspend fun clearLogs() {
        context.dataStore.edit { pref ->
            pref[KEY_SYNC_LOGS_JSON] = "[]"
        }
    }
}
