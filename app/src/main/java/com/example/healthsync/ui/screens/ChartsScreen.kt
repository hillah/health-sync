package com.example.healthsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthsync.HealthSyncUiState
import com.example.healthsync.ui.components.WeeklySleepBarChart
import com.example.healthsync.ui.components.WeeklyStepsBarChart
import com.example.healthsync.ui.components.WeeklyWeightLineChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    uiState: HealthSyncUiState,
    modifier: Modifier = Modifier
) {
    var selectedDays by remember { mutableStateOf(7) }

    val displayedSummaries = remember(uiState.weeklySummaries, selectedDays) {
        uiState.weeklySummaries.takeLast(selectedDays)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 期間セレクタ
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedDays == 7,
                    onClick = { selectedDays = 7 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("直近 7日間")
                }
                SegmentedButton(
                    selected = selectedDays == 14,
                    onClick = { selectedDays = 14 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("直近 14日間")
                }
            }
        }

        // 歩数チャート
        item {
            WeeklyStepsBarChart(summaries = displayedSummaries)
        }

        // 睡眠チャート
        item {
            WeeklySleepBarChart(summaries = displayedSummaries)
        }

        // 体重推移チャート
        item {
            WeeklyWeightLineChart(summaries = displayedSummaries)
        }
    }
}
