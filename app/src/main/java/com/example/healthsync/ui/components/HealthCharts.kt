package com.example.healthsync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthsync.data.model.HealthSummary
import com.example.healthsync.ui.theme.EmeraldGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WeeklyStepsBarChart(
    summaries: List<HealthSummary>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歩数 推移",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val avgSteps = if (summaries.isNotEmpty()) summaries.map { it.steps }.average().toLong() else 0L
                Text(
                    text = "平均: %,d 歩/日".format(avgSteps),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val maxSteps = (summaries.maxOfOrNull { it.steps } ?: 10000L).coerceAtLeast(5000L).toFloat()
            val primaryColor = MaterialTheme.colorScheme.primary

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(vertical = 8.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height - 40f
                val count = summaries.size.coerceAtLeast(1)
                val barWidth = (canvasWidth / count) * 0.45f
                val stepX = canvasWidth / count

                // 目標ライン (例: 8000歩)
                val targetY = canvasHeight - (8000f / maxSteps * canvasHeight)
                if (targetY in 0f..canvasHeight) {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, targetY),
                        end = Offset(canvasWidth, targetY),
                        strokeWidth = 2f
                    )
                }

                summaries.forEachIndexed { index, item ->
                    val x = index * stepX + (stepX - barWidth) / 2f
                    val barHeight = (item.steps / maxSteps * canvasHeight).coerceAtLeast(4f)
                    val y = canvasHeight - barHeight

                    // 棒
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor, primaryColor.copy(alpha = 0.6f))
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )

                    // 日付ラベル
                    val dayLabel = try {
                        val date = LocalDate.parse(item.date)
                        date.format(DateTimeFormatter.ofPattern("M/d"))
                    } catch (e: Exception) {
                        item.date.takeLast(4)
                    }

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawText(dayLabel, x + barWidth / 2f, size.height - 5f, paint)
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklySleepBarChart(
    summaries: List<HealthSummary>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "睡眠時間 推移",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val avgSleep = if (summaries.isNotEmpty()) summaries.map { it.sleepDurationMinutes }.average().toLong() else 0L
                val avgHours = avgSleep / 60
                val avgMins = avgSleep % 60
                Text(
                    text = "平均: ${avgHours}時間${avgMins}分",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val maxMinutes = (summaries.maxOfOrNull { it.sleepDurationMinutes } ?: 540L).coerceAtLeast(480L).toFloat()
            val sleepColor = Color(0xFF6366F1) // Indigo

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(vertical = 8.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height - 40f
                val count = summaries.size.coerceAtLeast(1)
                val barWidth = (canvasWidth / count) * 0.45f
                val stepX = canvasWidth / count

                summaries.forEachIndexed { index, item ->
                    val x = index * stepX + (stepX - barWidth) / 2f
                    val barHeight = (item.sleepDurationMinutes / maxMinutes * canvasHeight).coerceAtLeast(4f)
                    val y = canvasHeight - barHeight

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(sleepColor, sleepColor.copy(alpha = 0.5f))
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )

                    val dayLabel = try {
                        val date = LocalDate.parse(item.date)
                        date.format(DateTimeFormatter.ofPattern("M/d"))
                    } catch (e: Exception) {
                        item.date.takeLast(4)
                    }

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawText(dayLabel, x + barWidth / 2f, size.height - 5f, paint)
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyWeightLineChart(
    summaries: List<HealthSummary>,
    modifier: Modifier = Modifier
) {
    val validWeights = summaries.filter { it.latestWeightKg != null }
    if (validWeights.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "体重 推移",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val minWeight = (validWeights.minOfOrNull { it.latestWeightKg ?: 0.0 } ?: 50.0).toFloat() - 1f
            val maxWeight = (validWeights.maxOfOrNull { it.latestWeightKg ?: 100.0 } ?: 80.0).toFloat() + 1f
            val range = (maxWeight - minWeight).coerceAtLeast(2f)
            val chartColor = Color(0xFFEC4899) // Pink

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(vertical = 8.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height - 40f
                val count = summaries.size.coerceAtLeast(1)
                val stepX = canvasWidth / count

                val points = mutableListOf<Offset>()
                summaries.forEachIndexed { index, item ->
                    val weight = item.latestWeightKg?.toFloat()
                    if (weight != null) {
                        val x = index * stepX + stepX / 2f
                        val y = canvasHeight - ((weight - minWeight) / range * canvasHeight)
                        points.add(Offset(x, y))
                    }
                }

                if (points.size >= 2) {
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = chartColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )
                }

                points.forEach { point ->
                    drawCircle(color = chartColor, radius = 6f, center = point)
                    drawCircle(color = Color.White, radius = 3f, center = point)
                }

                summaries.forEachIndexed { index, item ->
                    val x = index * stepX + stepX / 2f
                    val dayLabel = try {
                        val date = LocalDate.parse(item.date)
                        date.format(DateTimeFormatter.ofPattern("M/d"))
                    } catch (e: Exception) {
                        item.date.takeLast(4)
                    }

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawText(dayLabel, x, size.height - 5f, paint)
                    }
                }
            }
        }
    }
}
