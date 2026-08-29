package com.example.healthsync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthsync.HealthSyncUiState
import com.example.healthsync.data.model.SyncLogItem
import com.example.healthsync.data.repository.AppSettings
import com.example.healthsync.ui.theme.EmeraldGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    uiState: HealthSyncUiState,
    onSaveWebhookSettings: (url: String, headerName: String, headerValue: String) -> Unit,
    onSaveScheduleSettings: (intervalHours: Long, isAutoSync: Boolean, includeDetailed: Boolean) -> Unit,
    onTestWebhook: (url: String, headerName: String, headerValue: String) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webhookUrl by remember(appSettings.webhookUrl) { mutableStateOf(appSettings.webhookUrl) }
    var headerName by remember(appSettings.customHeaderName) { mutableStateOf(appSettings.customHeaderName) }
    var headerValue by remember(appSettings.customHeaderValue) { mutableStateOf(appSettings.customHeaderValue) }

    var isAutoSync by remember(appSettings.isAutoSyncEnabled) { mutableStateOf(appSettings.isAutoSyncEnabled) }
    var intervalHours by remember(appSettings.syncIntervalHours) { mutableLongStateOf(appSettings.syncIntervalHours) }
    var includeDetailed by remember(appSettings.includeDetailedRecords) { mutableStateOf(appSettings.includeDetailedRecords) }

    var expandedIntervalMenu by remember { mutableStateOf(false) }
    val intervalOptions = listOf(1L, 3L, 6L, 12L, 24L)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Webhook エクスポート設定カード
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Webhook エクスポート設定",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = { webhookUrl = it },
                        label = { Text("Webhook エンドポイント URL") },
                        placeholder = { Text("https://example.com/api/health-sync") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = headerName,
                            onValueChange = { headerName = it },
                            label = { Text("認証ヘッダー名") },
                            placeholder = { Text("Authorization") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = headerValue,
                            onValueChange = { headerValue = it },
                            label = { Text("トークン / APIキー") },
                            placeholder = { Text("Bearer xxx") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSaveWebhookSettings(webhookUrl, headerName, headerValue) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("URL設定を保存")
                        }

                        OutlinedButton(
                            onClick = { onTestWebhook(webhookUrl, headerName, headerValue) },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSyncing && webhookUrl.isNotBlank(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("テスト送信")
                        }
                    }
                }
            }
        }

        // 2. 定期バックグラウンド同期 (WorkManager) 設定カード
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "自動同期 (WorkManager) スケジュール",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 自動同期トグル
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("定期バックグラウンド同期", fontWeight = FontWeight.SemiBold)
                            Text(
                                "バックグラウンドで自動的に最新データをWebhookへ送信",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutoSync,
                            onCheckedChange = {
                                isAutoSync = it
                                onSaveScheduleSettings(intervalHours, it, includeDetailed)
                            }
                        )
                    }

                    HorizontalDivider()

                    // 同期間隔ドロップダウン
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("同期間隔", fontWeight = FontWeight.Medium)

                        ExposedDropdownMenuBox(
                            expanded = expandedIntervalMenu,
                            onExpandedChange = { expandedIntervalMenu = !expandedIntervalMenu }
                        ) {
                            OutlinedTextField(
                                value = "${intervalHours}時間ごと",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIntervalMenu) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(150.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedIntervalMenu,
                                onDismissRequest = { expandedIntervalMenu = false }
                            ) {
                                intervalOptions.forEach { hours ->
                                    DropdownMenuItem(
                                        text = { Text("${hours}時間ごと") },
                                        onClick = {
                                            intervalHours = hours
                                            expandedIntervalMenu = false
                                            onSaveScheduleSettings(hours, isAutoSync, includeDetailed)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 詳細レコードを含めるか
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("詳細レコードも含める", fontWeight = FontWeight.Medium)
                            Text(
                                "個別歩数や睡眠ステージ等の明細データを送信",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = includeDetailed,
                            onCheckedChange = {
                                includeDetailed = it
                                onSaveScheduleSettings(intervalHours, isAutoSync, it)
                            }
                        )
                    }
                }
            }
        }

        // 3. 同期履歴ログヘッダー
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "送信ログ (直近履歴)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (appSettings.syncLogs.isNotEmpty()) {
                    TextButton(onClick = onClearLogs) {
                        Text("ログを消去")
                    }
                }
            }
        }

        // ログ一覧
        if (appSettings.syncLogs.isEmpty()) {
            item {
                Text(
                    text = "同期ログはまだありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(appSettings.syncLogs) { log ->
                SyncLogCard(log = log)
            }
        }
    }
}

@Composable
fun SyncLogCard(log: SyncLogItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (log.isSuccess) EmeraldGreen.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (log.isSuccess) "成功" else "失敗",
                            color = if (log.isSuccess) EmeraldGreen else Color.Red,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = log.syncType,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = log.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (log.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )

            if (log.recordsCountSummary.isNotBlank()) {
                Text(
                    text = log.recordsCountSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
