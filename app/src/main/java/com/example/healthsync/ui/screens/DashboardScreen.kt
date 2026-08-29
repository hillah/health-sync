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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import com.example.healthsync.HealthSyncUiState
import com.example.healthsync.data.model.StepItem
import com.example.healthsync.ui.components.SleepStagesBar
import com.example.healthsync.ui.components.SummaryMetricCard
import com.example.healthsync.ui.theme.EmeraldGreen

@Composable
fun DashboardScreen(
    uiState: HealthSyncUiState,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onManualSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. Health Connect SDK / Permission Check
        if (!uiState.hasPermissions || uiState.sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            item {
                PermissionStatusSection(
                    hasPermissions = uiState.hasPermissions,
                    sdkStatus = uiState.sdkStatus,
                    onRequestPermissions = onRequestPermissions
                )
            }
        }

        // 2. Quick Action Bar (同期 & 再取得)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onManualSync,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSyncing && uiState.hasPermissions,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Webhook送信中...")
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("クラウドへ即時同期")
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !uiState.isLoading && uiState.hasPermissions
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "再取得")
                }
            }
        }

        // 3. Error Banner
        if (uiState.errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 4. 今日の歩数
        item {
            val summary = uiState.todaySummary
            SummaryMetricCard(
                title = "今日の歩数",
                value = "%,d 歩".format(summary?.steps ?: 0L),
                subtitle = uiState.lastDataFetchTime?.let { "取得: $it" },
                icon = Icons.Default.DirectionsWalk,
                iconColor = MaterialTheme.colorScheme.primary
            )
        }

        // 5. 睡眠
        item {
            val summary = uiState.todaySummary
            val sleepMin = summary?.sleepDurationMinutes ?: 0L
            val hours = sleepMin / 60
            val mins = sleepMin % 60
            val sleepValueStr = if (sleepMin > 0) "${hours}時間 ${mins}分" else "未記録"

            SummaryMetricCard(
                title = "睡眠時間",
                value = sleepValueStr,
                icon = Icons.Default.Bedtime,
                iconColor = Color(0xFF6366F1),
                extraContent = {
                    val stages = summary?.sleepStages
                    if (stages != null && (stages.deepMinutes > 0 || stages.lightMinutes > 0 || stages.remMinutes > 0)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        SleepStagesBar(
                            deepMin = stages.deepMinutes,
                            lightMin = stages.lightMinutes,
                            remMin = stages.remMinutes,
                            awakeMin = stages.awakeMinutes
                        )
                    }
                }
            )
        }

        // 6. 体重 & 体脂肪
        item {
            val summary = uiState.todaySummary
            val weightStr = summary?.latestWeightKg?.let { "%.1f kg".format(it) } ?: "未記録"
            val fatStr = summary?.latestBodyFatPercent?.let { "体脂肪: %.1f %%".format(it) }

            SummaryMetricCard(
                title = "体重 / 体組成",
                value = weightStr,
                subtitle = fatStr,
                icon = Icons.Default.Scale,
                iconColor = Color(0xFFEC4899)
            )
        }

        // 7. 心拍数
        item {
            val summary = uiState.todaySummary
            val avgHr = summary?.avgHeartRateBpm
            val restingHr = summary?.restingHeartRateBpm
            val hrValue = if (avgHr != null) "$avgHr bpm" else "未記録"
            val hrSub = restingHr?.let { "安静時: $it bpm" }

            SummaryMetricCard(
                title = "心拍数 (平均)",
                value = hrValue,
                subtitle = hrSub,
                icon = Icons.Default.Favorite,
                iconColor = Color(0xFFEF4444)
            )
        }

        // 8. 消費カロリー
        item {
            val summary = uiState.todaySummary
            val totalCal = summary?.totalCaloriesKcal ?: 0.0
            val activeCal = summary?.activeCaloriesKcal ?: 0.0
            val calStr = if (totalCal > 0.0) "%,d kcal".format(totalCal.toLong()) else if (activeCal > 0.0) "%,d kcal (アクティブ)".format(activeCal.toLong()) else "未記録"

            SummaryMetricCard(
                title = "消費エネルギー",
                value = calStr,
                subtitle = if (activeCal > 0.0 && totalCal > 0.0) "アクティブ: %,d kcal".format(activeCal.toLong()) else null,
                icon = Icons.Default.LocalFireDepartment,
                iconColor = Color(0xFFF97316)
            )
        }

        // 9. 血圧
        item {
            val summary = uiState.todaySummary
            val systolic = summary?.latestSystolicMmHg
            val diastolic = summary?.latestDiastolicMmHg
            val bpStr = if (systolic != null && diastolic != null) {
                "%.0f / %.0f mmHg".format(systolic, diastolic)
            } else if (systolic != null) {
                "%.0f mmHg (最高)".format(systolic)
            } else {
                "未記録"
            }

            SummaryMetricCard(
                title = "血圧 (最新)",
                value = bpStr,
                icon = Icons.Default.MonitorHeart,
                iconColor = Color(0xFF8B5CF6)
            )
        }

        // 10. 栄養摂取
        item {
            val summary = uiState.todaySummary
            val energy = summary?.dietaryEnergyKcal ?: 0.0
            val energyStr = if (energy > 0.0) "%,d kcal".format(energy.toLong()) else "未記録"
            val p = summary?.dietaryProteinGrams ?: 0.0
            val f = summary?.dietaryFatGrams ?: 0.0
            val c = summary?.dietaryCarbsGrams ?: 0.0
            val pfcStr = if (p > 0 || f > 0 || c > 0) {
                "P: %.1fg / F: %.1fg / C: %.1fg".format(p, f, c)
            } else null

            SummaryMetricCard(
                title = "栄養・食事摂取",
                value = energyStr,
                subtitle = pfcStr,
                icon = Icons.Default.Restaurant,
                iconColor = Color(0xFF10B981)
            )
        }
    }
}

@Composable
fun PermissionStatusSection(
    hasPermissions: Boolean,
    sdkStatus: Int,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Health Connect 連携",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (hasPermissions) EmeraldGreen.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasPermissions) "許可済" else "要許可",
                        color = if (hasPermissions) EmeraldGreen else Color.Red,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!hasPermissions) {
                Text(
                    text = "歩数・睡眠・体重・心拍数・カロリーのデータ読み取り権限が必要です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("全権限を一括リクエスト")
                }
            }
        }
    }
}
