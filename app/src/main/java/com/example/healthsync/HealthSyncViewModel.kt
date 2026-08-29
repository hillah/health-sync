package com.example.healthsync

import android.app.Application
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class HealthSyncUiState(
    val sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val hasPermissions: Boolean = false,
    val isLoading: Boolean = false,
    val todaySteps: Long? = null,
    val stepRecords: List<StepsRecord> = emptyList(),
    val lastSyncTime: String? = null,
    val errorMessage: String? = null
)

class HealthSyncViewModel(application: Application) : AndroidViewModel(application) {

    val healthConnectManager = HealthConnectManager(application)

    private val _uiState = MutableStateFlow(HealthSyncUiState())
    val uiState: StateFlow<HealthSyncUiState> = _uiState.asStateFlow()

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
                    fetchTodaySteps()
                }
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            val hasPerms = healthConnectManager.hasAllPermissions()
            _uiState.update { it.copy(hasPermissions = hasPerms) }
            if (hasPerms) {
                fetchTodaySteps()
            }
        }
    }

    fun fetchTodaySteps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val totalSteps = healthConnectManager.getTodayStepsTotal()
                
                val now = ZonedDateTime.now()
                val startOfDay = now.toLocalDate().atStartOfDay(now.zone).toInstant()
                val records = healthConnectManager.getStepRecords(startOfDay, now.toInstant())

                val timeStr = DateTimeFormatter.ofPattern("HH:mm:ss").format(now)
                
                Log.d("HealthSync", "Fetched today steps: $totalSteps, records count: ${records.size}")
                records.forEach { record ->
                    Log.d("HealthSync", "Record: ${record.startTime} ~ ${record.endTime} -> ${record.count} steps (source: ${record.metadata.dataOrigin.packageName})")
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        todaySteps = totalSteps,
                        stepRecords = records,
                        lastSyncTime = timeStr
                    )
                }
            } catch (e: Exception) {
                Log.e("HealthSync", "Error fetching steps", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "歩数の取得に失敗しました"
                    )
                }
            }
        }
    }
}
