package com.example.healthsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.health.connect.client.PermissionController
import com.example.healthsync.ui.screens.DashboardScreen
import com.example.healthsync.ui.screens.SettingsScreen
import com.example.healthsync.ui.theme.HealthSyncTheme
import kotlinx.coroutines.launch

enum class MainTab(val title: String) {
    DASHBOARD("ダッシュボード"),
    SETTINGS("設定・同期")
}

class MainActivity : ComponentActivity() {

    private val viewModel: HealthSyncViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HealthSyncTheme {
                val uiState by viewModel.uiState.collectAsState()
                val appSettings by viewModel.appSettings.collectAsState()

                var selectedTab by remember { mutableStateOf(MainTab.DASHBOARD) }
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                // Health Connect permission request launcher
                val requestPermissionLauncher = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) { granted ->
                    android.util.Log.d("HealthSync", "Permissions result callback: $granted")
                    viewModel.onPermissionsResult(granted)
                }

                // スナックバーフィードバック表示
                LaunchedEffect(uiState.syncFeedbackMessage) {
                    uiState.syncFeedbackMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearFeedbackMessage()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "HealthSync",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            actions = {
                                if (selectedTab == MainTab.DASHBOARD) {
                                    IconButton(
                                        onClick = { viewModel.fetchAllHealthData() },
                                        enabled = uiState.hasPermissions && !uiState.isLoading
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "更新"
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == MainTab.DASHBOARD,
                                onClick = { selectedTab = MainTab.DASHBOARD },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                label = { Text(MainTab.DASHBOARD.title) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.SETTINGS,
                                onClick = { selectedTab = MainTab.SETTINGS },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text(MainTab.SETTINGS.title) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            MainTab.DASHBOARD -> {
                                DashboardScreen(
                                    uiState = uiState,
                                    onRequestPermissions = {
                                        val perms = viewModel.healthConnectManager.permissions
                                        android.util.Log.d("HealthSync", "Launching permission request for: $perms")
                                        requestPermissionLauncher.launch(perms)
                                    },
                                    onRefresh = { viewModel.fetchAllHealthData() },
                                    onManualSync = { viewModel.manualExportWebhook() }
                                )
                            }
                            MainTab.SETTINGS -> {
                                SettingsScreen(
                                    appSettings = appSettings,
                                    uiState = uiState,
                                    onSaveWebhookSettings = { url, headerName, headerValue ->
                                        viewModel.saveWebhookSettings(url, headerName, headerValue)
                                    },
                                    onSaveScheduleSettings = { intervalHours, isAutoSync, includeDetailed ->
                                        viewModel.saveScheduleSettings(intervalHours, isAutoSync, includeDetailed)
                                    },
                                    onTestWebhook = { url, headerName, headerValue ->
                                        viewModel.testWebhook(url, headerName, headerValue)
                                    },
                                    onClearLogs = { viewModel.clearLogs() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkStatusAndPermissions()
    }
}
