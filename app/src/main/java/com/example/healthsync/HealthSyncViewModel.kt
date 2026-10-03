package com.example.healthsync

import android.app.Application
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthsync.data.model.DetailedRecords
import com.example.healthsync.data.model.HealthSummary
import com.example.healthsync.data.model.HealthSyncPayload
import com.example.healthsync.data.model.SyncLogItem
import com.example.healthsync.data.network.WebhookClient
import com.example.healthsync.data.network.WebhookResult
import com.example.healthsync.data.repository.AppSettings
import com.example.healthsync.data.repository.SettingsRepository
import com.example.healthsync.worker.WorkManagerHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class HealthSyncUiState(
    val sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val hasPermissions: Boolean = false,
    val isLoading: Boolean = false,
    val isAnalyticsLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val todaySummary: HealthSummary? = null,
    val weeklySummaries: List<HealthSummary> = emptyList(),
    val monthlySummaries: List<HealthSummary> = emptyList(), // 直近30日（昨日末尾）
    val detailedRecords: DetailedRecords? = null,
    val lastDataFetchTime: String? = null,
    val errorMessage: String? = null,
    val syncFeedbackMessage: String? = null
)

class HealthSyncViewModel(application: Application) : AndroidViewModel(application) {

    val healthConnectManager = HealthConnectManager(application)
    val settingsRepository = SettingsRepository(application)
    private val webhookClient = WebhookClient()

    private val _uiState = MutableStateFlow(HealthSyncUiState())
    val uiState: StateFlow<HealthSyncUiState> = _uiState.asStateFlow()

    val appSettings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    init {
        checkStatusAndPermissions()
    }

    fun checkStatusAndPermissions() {
        val status = healthConnectManager.getSdkStatus()
        _uiState.update { it.copy(sdkStatus = status) }

        if (status == HealthConnectClient.SDK_AVAILABLE) {
            viewModelScope.launch {
                val hasPerms = healthConnectManager.hasAllPermissions()
                _uiState.update { it.copy(hasPermissions = hasPerms) }
                if (hasPerms) {
                    fetchAllHealthData()
                }
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            val hasPerms = healthConnectManager.hasAllPermissions()
            _uiState.update { it.copy(hasPermissions = hasPerms) }
            if (hasPerms) {
                fetchAllHealthData()
            }
        }
    }

    /**
     * 全健康データ（本日サマリー・推移・詳細レコード・月間分析）を一括読み込み
     */
    fun fetchAllHealthData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isAnalyticsLoading = true, errorMessage = null) }
            try {
                val today = LocalDate.now()
                val yesterday = today.minusDays(1)
                val summary = healthConnectManager.getHealthSummaryForDay(today)
                val weekly = healthConnectManager.getDailyHealthSummaries(days = 14)
                val monthly = healthConnectManager.getDailyHealthSummaries(days = 30, endDate = yesterday)
                val detailed = healthConnectManager.getTodayDetailedRecords()

                val timeStr = DateTimeFormatter.ofPattern("HH:mm:ss").format(ZonedDateTime.now())

                Log.d("HealthSyncVM", "Fetched all health data. Today steps: ${summary.steps}, Monthly: ${monthly.size} days")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAnalyticsLoading = false,
                        todaySummary = summary,
                        weeklySummaries = weekly,
                        monthlySummaries = monthly,
                        detailedRecords = detailed,
                        lastDataFetchTime = timeStr
                    )
                }
            } catch (e: Exception) {
                Log.e("HealthSyncVM", "Error fetching health data", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAnalyticsLoading = false,
                        errorMessage = e.localizedMessage ?: "健康データの取得に失敗しました"
                    )
                }
            }
        }
    }

    /**
     * 分析用30日データ（昨日末尾）の単体再取得
     */
    fun fetchMonthlyAnalyticsData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyticsLoading = true) }
            try {
                val yesterday = LocalDate.now().minusDays(1)
                val monthly = healthConnectManager.getDailyHealthSummaries(days = 30, endDate = yesterday)
                _uiState.update {
                    it.copy(
                        isAnalyticsLoading = false,
                        monthlySummaries = monthly
                    )
                }
            } catch (e: Exception) {
                Log.e("HealthSyncVM", "Error fetching monthly analytics", e)
                _uiState.update { it.copy(isAnalyticsLoading = false) }
            }
        }
    }

    /**
     * 手動で今すぐ Webhook 送信
     */
    fun manualExportWebhook() {
        viewModelScope.launch {
            val settings = appSettings.value
            if (settings.webhookUrl.isBlank()) {
                _uiState.update { it.copy(errorMessage = "設定画面で Webhook URL を指定してください") }
                return@launch
            }

            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

            val now = ZonedDateTime.now()
            val isoTimestamp = DateTimeFormatter.ISO_INSTANT.format(now.toInstant())
            val logTimeStr = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss").format(now)

            try {
                val today = LocalDate.now()
                val summary = healthConnectManager.getHealthSummaryForDay(today)
                val recentSummaries = healthConnectManager.getDailyHealthSummaries(days = 7)
                val detailed = if (settings.includeDetailedRecords) {
                    healthConnectManager.getTodayDetailedRecords()
                } else {
                    null
                }

                val payload = HealthSyncPayload(
                    timestamp = isoTimestamp,
                    deviceId = android.os.Build.MODEL,
                    syncType = "MANUAL",
                    summary = summary,
                    recentSummaries = recentSummaries,
                    detailedRecords = detailed
                )

                val result = webhookClient.sendPayload(
                    url = settings.webhookUrl,
                    payload = payload,
                    customHeaderName = settings.customHeaderName,
                    customHeaderValue = settings.customHeaderValue
                )

                when (result) {
                    is WebhookResult.Success -> {
                        val log = SyncLogItem(
                            id = UUID.randomUUID().toString(),
                            timestamp = logTimeStr,
                            isSuccess = true,
                            syncType = "手動即時同期",
                            statusCode = result.statusCode,
                            message = "送信成功 (HTTP ${result.statusCode})",
                            recordsCountSummary = "歩数: ${summary.steps}, 睡眠: ${summary.sleepDurationMinutes}分"
                        )
                        settingsRepository.addSyncLog(log)
                        _uiState.update { it.copy(isSyncing = false, syncFeedbackMessage = "Webhookへの送信が完了しました") }
                    }
                    is WebhookResult.Failure -> {
                        val log = SyncLogItem(
                            id = UUID.randomUUID().toString(),
                            timestamp = logTimeStr,
                            isSuccess = false,
                            syncType = "手動即時同期",
                            statusCode = result.statusCode,
                            message = result.errorMessage,
                            recordsCountSummary = "送信失敗"
                        )
                        settingsRepository.addSyncLog(log)
                        _uiState.update { it.copy(isSyncing = false, errorMessage = "送信失敗: ${result.errorMessage}") }
                    }
                }
            } catch (e: Exception) {
                Log.e("HealthSyncVM", "Manual export error", e)
                _uiState.update { it.copy(isSyncing = false, errorMessage = "送信エラー: ${e.localizedMessage}") }
            }
        }
    }

    /**
     * Webhook 設定のテスト送信
     */
    fun testWebhook(url: String, headerName: String, headerValue: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
            val now = ZonedDateTime.now()
            val isoTimestamp = DateTimeFormatter.ISO_INSTANT.format(now.toInstant())
            val logTimeStr = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss").format(now)

            val testSummary = uiState.value.todaySummary ?: HealthSummary(date = LocalDate.now().toString(), steps = 7777L)

            val payload = HealthSyncPayload(
                timestamp = isoTimestamp,
                deviceId = android.os.Build.MODEL,
                syncType = "TEST_PING",
                summary = testSummary
            )

            val result = webhookClient.sendPayload(
                url = url,
                payload = payload,
                customHeaderName = headerName,
                customHeaderValue = headerValue
            )

            when (result) {
                is WebhookResult.Success -> {
                    val log = SyncLogItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = logTimeStr,
                        isSuccess = true,
                        syncType = "テスト送信",
                        statusCode = result.statusCode,
                        message = "テスト送信成功 (HTTP ${result.statusCode})",
                        recordsCountSummary = "疎通確認OK"
                    )
                    settingsRepository.addSyncLog(log)
                    _uiState.update { it.copy(isSyncing = false, syncFeedbackMessage = "テスト送信に成功しました！(HTTP ${result.statusCode})") }
                }
                is WebhookResult.Failure -> {
                    val log = SyncLogItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = logTimeStr,
                        isSuccess = false,
                        syncType = "テスト送信",
                        statusCode = result.statusCode,
                        message = result.errorMessage,
                        recordsCountSummary = "疎通エラー"
                    )
                    settingsRepository.addSyncLog(log)
                    _uiState.update { it.copy(isSyncing = false, errorMessage = "テスト送信失敗: ${result.errorMessage}") }
                }
            }
        }
    }

    fun saveWebhookSettings(url: String, headerName: String, headerValue: String) {
        viewModelScope.launch {
            settingsRepository.updateWebhookSettings(url, headerName, headerValue)
            _uiState.update { it.copy(syncFeedbackMessage = "Webhook設定を保存しました") }
        }
    }

    fun saveScheduleSettings(intervalHours: Long, isAutoSync: Boolean, includeDetailed: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSyncScheduleSettings(intervalHours, isAutoSync, includeDetailed)
            // WorkManager スケジュールを更新
            WorkManagerHelper.updatePeriodicSync(
                context = getApplication(),
                intervalHours = intervalHours,
                isEnabled = isAutoSync
            )
            _uiState.update { it.copy(syncFeedbackMessage = "同期スケジュールを更新しました") }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            settingsRepository.clearLogs()
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(syncFeedbackMessage = null) }
    }
}
