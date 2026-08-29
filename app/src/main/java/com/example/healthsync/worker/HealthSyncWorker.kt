package com.example.healthsync.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.healthsync.HealthConnectManager
import com.example.healthsync.data.model.HealthSyncPayload
import com.example.healthsync.data.model.SyncLogItem
import com.example.healthsync.data.network.WebhookClient
import com.example.healthsync.data.network.WebhookResult
import com.example.healthsync.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class HealthSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("HealthSyncWorker", "Background sync worker started execution")

        val settingsRepo = SettingsRepository(context)
        val settings = settingsRepo.settingsFlow.first()

        if (settings.webhookUrl.isBlank()) {
            Log.w("HealthSyncWorker", "Webhook URL is empty. Skipping background sync.")
            return Result.success()
        }

        val healthConnectManager = HealthConnectManager(context)
        if (healthConnectManager.getSdkStatus() != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
            Log.w("HealthSyncWorker", "Health Connect is not available.")
            return Result.failure()
        }

        val now = ZonedDateTime.now()
        val isoTimestamp = DateTimeFormatter.ISO_INSTANT.format(now.toInstant())
        val logTimeStr = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss").format(now)

        return try {
            val todaySummary = healthConnectManager.getHealthSummaryForDay(LocalDate.now())
            val detailed = if (settings.includeDetailedRecords) {
                healthConnectManager.getTodayDetailedRecords()
            } else {
                null
            }

            val payload = HealthSyncPayload(
                timestamp = isoTimestamp,
                deviceId = android.os.Build.MODEL,
                syncType = "BACKGROUND_WORKER",
                summary = todaySummary,
                detailedRecords = detailed
            )

            val webhookClient = WebhookClient()
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
                        syncType = "定期バックグラウンド同期",
                        statusCode = result.statusCode,
                        message = "送信成功 (HTTP ${result.statusCode})",
                        recordsCountSummary = "歩数: ${todaySummary.steps}, 睡眠: ${todaySummary.sleepDurationMinutes}分"
                    )
                    settingsRepo.addSyncLog(log)
                    Log.i("HealthSyncWorker", "Background sync finished successfully")
                    Result.success()
                }
                is WebhookResult.Failure -> {
                    val log = SyncLogItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = logTimeStr,
                        isSuccess = false,
                        syncType = "定期バックグラウンド同期",
                        statusCode = result.statusCode,
                        message = result.errorMessage,
                        recordsCountSummary = "送信失敗"
                    )
                    settingsRepo.addSyncLog(log)
                    Log.e("HealthSyncWorker", "Background sync failed: ${result.errorMessage}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e("HealthSyncWorker", "Exception in background worker", e)
            val log = SyncLogItem(
                id = UUID.randomUUID().toString(),
                timestamp = logTimeStr,
                isSuccess = false,
                syncType = "定期バックグラウンド同期",
                statusCode = null,
                message = e.localizedMessage ?: "不明なエラー",
                recordsCountSummary = "例外発生"
            )
            settingsRepo.addSyncLog(log)
            Result.retry()
        }
    }
}
