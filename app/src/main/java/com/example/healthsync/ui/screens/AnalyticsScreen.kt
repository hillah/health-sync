package com.example.healthsync.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthsync.data.model.HealthSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

// チャートカラー定義
private val ColorBurned = Color(0xFF0EA5E9)      // 消費: スカイブルー
private val ColorIntake = Color(0xFFEF4444)      // 摂取: コーラルレッド
private val ColorBalance = Color(0xFFFBBF24)     // 収支折れ線: ゴールドイエロー
private val ColorFat = Color(0xFFF59E0B)         // 脂質 (F): アンバーイエロー
private val ColorCarbs = Color(0xFF10B981)       // 炭水化物 (C): エメラルドグリーン
private val ColorProtein = Color(0xFF06B6D4)     // タンパク質 (P): シアンブルー
private val ColorGuideline = Color(0x66FFFFFF)   // 補助線
private val ColorMonthDivider = Color(0x5538BDF8) // 月境界線

@Composable
fun AnalyticsScreen(
    monthlySummaries: List<HealthSummary>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {}
) {
    var selectedEnergyIndex by remember { mutableStateOf<Int?>(null) }
    var selectedPfcIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        if (isLoading && monthlySummaries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }
        } else if (monthlySummaries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "直近30日分のデータがありません",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            // 2. グラフ１：エネルギー推移
            item {
                EnergyMirrorCard(
                    summaries = monthlySummaries,
                    selectedIndex = selectedEnergyIndex,
                    onSelectIndex = { selectedEnergyIndex = it }
                )
            }

            // 3. グラフ２：PFCバランス
            item {
                PfcStackedCard(
                    summaries = monthlySummaries,
                    selectedIndex = selectedPfcIndex,
                    onSelectIndex = { selectedPfcIndex = it }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 1. エネルギー推移カード（上下反転ミラー ＋ 収支折れ線）
// -------------------------------------------------------------
@Composable
fun EnergyMirrorCard(
    summaries: List<HealthSummary>,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    // 集計計算
    var burnedWinCount = 0
    var totalNetBalance = 0.0
    var validDaysCount = 0

    for (s in summaries) {
        val b = s.totalCaloriesKcal
        val i = s.dietaryEnergyKcal
        if (b > 0 || i > 0) {
            val net = b - i
            if (net > 0) burnedWinCount++
            totalNetBalance += net
            validDaysCount++
        }
    }

    val avgNet = if (validDaysCount > 0) totalNetBalance / validDaysCount else 0.0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // タイトル & サマリーバッジ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "エネルギー推移",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val sign = if (avgNet >= 0) "+" else ""
                    Text(
                        text = "消費勝ち ${burnedWinCount}日 / 平均収支 ${sign}%.0f kcal".format(avgNet),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (avgNet >= 0) ColorBurned else ColorIntake
                    )
                }
            }

            // 凡例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = ColorBurned, label = "消費")
                LegendItem(color = ColorIntake, label = "摂取")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ColorBalance)
                    )
                    Text(
                        text = "収支 (消費−摂取)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // タップ時の詳細インフォ表示
            AnimatedVisibility(visible = selectedIndex != null && selectedIndex in summaries.indices) {
                val s = selectedIndex?.let { summaries.getOrNull(it) }
                if (s != null) {
                    val burned = s.totalCaloriesKcal
                    val intake = s.dietaryEnergyKcal
                    val diff = burned - intake
                    val dateLabel = formatFullDate(s.date)

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "消費: %,d".format(burned.toLong()),
                                    color = ColorBurned,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "摂取: %,d".format(intake.toLong()),
                                    color = ColorIntake,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                val diffSign = if (diff >= 0) "+" else ""
                                Text(
                                    text = "収支: $diffSign%,d".format(diff.toLong()),
                                    color = if (diff >= 0) ColorBurned else ColorIntake,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // チャート描画領域
            EnergyMirrorChartCanvas(
                summaries = summaries,
                selectedIndex = selectedIndex,
                onSelectIndex = onSelectIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )
        }
    }
}

@Composable
fun EnergyMirrorChartCanvas(
    summaries: List<HealthSummary>,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (summaries.isEmpty()) return

    // 最大値の計算（0ラインを中心に上下のスケールを均等にする）
    var maxVal = 2500f
    for (s in summaries) {
        maxVal = max(maxVal, s.totalCaloriesKcal.toFloat())
        maxVal = max(maxVal, s.dietaryEnergyKcal.toFloat())
    }
    // 上限に余白を持たせる
    maxVal = (maxVal * 1.15f).coerceAtLeast(1500f)

    Canvas(
        modifier = modifier
            .pointerInput(summaries) {
                detectTapGestures { offset ->
                    val totalBars = summaries.size
                    val paddingLeft = 32.dp.toPx()
                    val paddingRight = 12.dp.toPx()
                    val chartWidth = size.width - paddingLeft - paddingRight
                    val step = chartWidth / totalBars

                    val x = offset.x - paddingLeft
                    val idx = (x / step).toInt().coerceIn(0, totalBars - 1)
                    if (selectedIndex == idx) {
                        onSelectIndex(null)
                    } else {
                        onSelectIndex(idx)
                    }
                }
            }
    ) {
        val totalBars = summaries.size
        val paddingLeft = 32.dp.toPx()
        val paddingRight = 12.dp.toPx()
        val paddingTop = 12.dp.toPx()
        val paddingBottom = 24.dp.toPx()

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom
        val zeroY = paddingTop + (chartHeight / 2f) // 中央の 0 基準線
        val halfHeight = chartHeight / 2f

        val step = chartWidth / totalBars
        val barWidth = (step * 0.65f).coerceAtLeast(3f)

        // 1. 水平グリッド線（+2000, 0, -2000 等）
        val gridLevels = listOf(0.75f, 0.35f, 0f, -0.35f, -0.75f)
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }

        for (lvl in gridLevels) {
            val y = zeroY - (lvl * halfHeight)
            val calValue = (lvl * maxVal).toInt()
            val dashEffect = if (lvl == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

            drawLine(
                color = if (lvl == 0f) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = if (lvl == 0f) 1.5.dp.toPx() else 1.dp.toPx(),
                pathEffect = dashEffect
            )

            // Y軸ラベル
            val labelText = if (lvl == 0f) "0" else "${calValue}"
            drawContext.canvas.nativeCanvas.drawText(
                labelText,
                paddingLeft - 4.dp.toPx(),
                y + 3.dp.toPx(),
                textPaint
            )
        }

        // 2. バー描画 & 月境界線検出
        val balancePoints = mutableListOf<Offset>()
        val monthBoundaryIndices = mutableListOf<Int>()

        var prevMonth: Int? = null
        for (i in 0 until totalBars) {
            val date = parseLocalDate(summaries[i].date)
            if (date != null) {
                if (prevMonth != null && date.monthValue != prevMonth) {
                    monthBoundaryIndices.add(i)
                }
                prevMonth = date.monthValue
            }
        }

        // 月境界の垂直線描画
        for (idx in monthBoundaryIndices) {
            val dividerX = paddingLeft + (idx * step)
            drawLine(
                color = ColorMonthDivider,
                start = Offset(dividerX, paddingTop),
                end = Offset(dividerX, size.height - paddingBottom),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
        }

        // 各日のバー描画
        for (i in 0 until totalBars) {
            val s = summaries[i]
            val x = paddingLeft + (i * step) + (step / 2f)
            val left = x - (barWidth / 2f)

            val burned = s.totalCaloriesKcal.toFloat()
            val intake = s.dietaryEnergyKcal.toFloat()

            // 選択日の背景ハイライト
            if (selectedIndex == i) {
                drawRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(paddingLeft + (i * step), paddingTop),
                    size = Size(step, chartHeight)
                )
            }

            // 上向きバー: 消費 (0 から上へ)
            if (burned > 0) {
                val barH = (burned / maxVal) * halfHeight
                drawRoundRect(
                    color = ColorBurned,
                    topLeft = Offset(left, zeroY - barH),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }

            // 下向きバー: 摂取 (0 から下へ)
            if (intake > 0) {
                val barH = (intake / maxVal) * halfHeight
                drawRoundRect(
                    color = ColorIntake,
                    topLeft = Offset(left, zeroY),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }

            // 収支折れ線用ポイント
            val net = burned - intake
            val netY = zeroY - ((net / maxVal) * halfHeight).coerceIn(-halfHeight, halfHeight)
            balancePoints.add(Offset(x, netY))
        }

        // 3. 収支折れ線グラフ描画 (黄色のライン ＋ ドット)
        if (balancePoints.size > 1) {
            val linePath = Path()
            linePath.moveTo(balancePoints[0].x, balancePoints[0].y)
            for (pt in balancePoints) {
                linePath.lineTo(pt.x, pt.y)
            }
            drawPath(
                path = linePath,
                color = ColorBalance,
                style = Stroke(width = 2.dp.toPx())
            )

            // 各点のドット
            for (pt in balancePoints) {
                drawCircle(
                    color = ColorBalance,
                    radius = 2.2.dp.toPx(),
                    center = pt
                )
            }
        }

        // 4. X軸日付ラベル描画
        val datePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val monthPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#38BDF8") // スカイブルー強調
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val yPos = size.height - 6.dp.toPx()

        for (i in 0 until totalBars) {
            val date = parseLocalDate(summaries[i].date) ?: continue
            val x = paddingLeft + (i * step) + (step / 2f)

            // 月初（1日）
            if (date.dayOfMonth == 1) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${date.monthValue}/1",
                    x,
                    yPos,
                    monthPaint
                )
            } else if (i == 0) {
                // 最左端（開始日）
                drawContext.canvas.nativeCanvas.drawText(
                    "${date.monthValue}/${date.dayOfMonth}",
                    x,
                    yPos,
                    datePaint
                )
            } else if (i == totalBars - 1) {
                // 最右端
                drawContext.canvas.nativeCanvas.drawText(
                    "${date.dayOfMonth}",
                    x,
                    yPos,
                    datePaint
                )
            } else if (i % 6 == 0 && (i + 1) < totalBars - 2 && date.dayOfMonth != 1) {
                // 補助目盛り（数日おき）
                drawContext.canvas.nativeCanvas.drawText(
                    "${date.dayOfMonth}",
                    x,
                    yPos,
                    datePaint
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 2. PFCバランスカード（罪悪感順スタック ＋ 30%・80%補助線）
// -------------------------------------------------------------
@Composable
fun PfcStackedCard(
    summaries: List<HealthSummary>,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    // 月間平均PFC比率計算
    var totalP = 0.0
    var totalF = 0.0
    var totalC = 0.0

    for (s in summaries) {
        totalP += s.dietaryProteinGrams * 4.0
        totalF += s.dietaryFatGrams * 9.0
        totalC += s.dietaryCarbsGrams * 4.0
    }
    val grandTotalCal = totalP + totalF + totalC
    val pPct = if (grandTotalCal > 0) (totalP / grandTotalCal * 100).toInt() else 0
    val fPct = if (grandTotalCal > 0) (totalF / grandTotalCal * 100).toInt() else 0
    val cPct = if (grandTotalCal > 0) (totalC / grandTotalCal * 100).toInt() else 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // タイトル & サマリーバッジ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PFCバランス",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "月間平均 P:$pPct% F:$fPct% C:$cPct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 凡例（下からの順：脂質 ➔ 炭水化物 ➔ タンパク質）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = ColorFat, label = "脂質 (F)")
                LegendItem(color = ColorCarbs, label = "炭水化物 (C)")
                LegendItem(color = ColorProtein, label = "タンパク質 (P)")
            }

            // タップ時の詳細インフォ表示
            AnimatedVisibility(visible = selectedIndex != null && selectedIndex in summaries.indices) {
                val s = selectedIndex?.let { summaries.getOrNull(it) }
                if (s != null) {
                    val pGrams = s.dietaryProteinGrams
                    val fGrams = s.dietaryFatGrams
                    val cGrams = s.dietaryCarbsGrams
                    val dayCal = (pGrams * 4.0) + (fGrams * 9.0) + (cGrams * 4.0)

                    val pDayPct = if (dayCal > 0) (pGrams * 4.0 / dayCal * 100).toInt() else 0
                    val fDayPct = if (dayCal > 0) (fGrams * 9.0 / dayCal * 100).toInt() else 0
                    val cDayPct = if (dayCal > 0) (cGrams * 4.0 / dayCal * 100).toInt() else 0
                    val dateLabel = formatFullDate(s.date)

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "F: ${fGrams.toInt()}g ($fDayPct%)",
                                    color = ColorFat,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "C: ${cGrams.toInt()}g ($cDayPct%)",
                                    color = ColorCarbs,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "P: ${pGrams.toInt()}g ($pDayPct%)",
                                    color = ColorProtein,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // PFC 100% スタックチャート描画
            PfcStackedChartCanvas(
                summaries = summaries,
                selectedIndex = selectedIndex,
                onSelectIndex = onSelectIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

@Composable
fun PfcStackedChartCanvas(
    summaries: List<HealthSummary>,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (summaries.isEmpty()) return

    Canvas(
        modifier = modifier
            .pointerInput(summaries) {
                detectTapGestures { offset ->
                    val totalBars = summaries.size
                    val paddingLeft = 32.dp.toPx()
                    val paddingRight = 12.dp.toPx()
                    val chartWidth = size.width - paddingLeft - paddingRight
                    val step = chartWidth / totalBars

                    val x = offset.x - paddingLeft
                    val idx = (x / step).toInt().coerceIn(0, totalBars - 1)
                    if (selectedIndex == idx) {
                        onSelectIndex(null)
                    } else {
                        onSelectIndex(idx)
                    }
                }
            }
    ) {
        val totalBars = summaries.size
        val paddingLeft = 32.dp.toPx()
        val paddingRight = 12.dp.toPx()
        val paddingTop = 12.dp.toPx()
        val paddingBottom = 24.dp.toPx()

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom
        val step = chartWidth / totalBars
        val barWidth = (step * 0.7f).coerceAtLeast(3f)

        // 1. 補助線（30% 脂質リミット線 & 80% タンパク質確保ライン）
        val guideLinePaint = android.graphics.Paint().apply {
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }

        val guidelines = listOf(
            Pair(0.30f, ColorFat),    // 30% 脂質上限
            Pair(0.80f, ColorProtein) // 80% タンパク質ライン
        )

        for ((pct, color) in guidelines) {
            val y = (paddingTop + chartHeight) - (pct * chartHeight)
            drawLine(
                color = color.copy(alpha = 0.6f),
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )

            guideLinePaint.color = android.graphics.Color.argb(
                (0.8f * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt()
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${(pct * 100).toInt()}%",
                paddingLeft - 4.dp.toPx(),
                y + 3.dp.toPx(),
                guideLinePaint
            )
        }

        // 月境界線検出
        var prevMonth: Int? = null
        val monthBoundaryIndices = mutableListOf<Int>()
        for (i in 0 until totalBars) {
            val date = parseLocalDate(summaries[i].date)
            if (date != null) {
                if (prevMonth != null && date.monthValue != prevMonth) {
                    monthBoundaryIndices.add(i)
                }
                prevMonth = date.monthValue
            }
        }

        for (idx in monthBoundaryIndices) {
            val dividerX = paddingLeft + (idx * step)
            drawLine(
                color = ColorMonthDivider,
                start = Offset(dividerX, paddingTop),
                end = Offset(dividerX, size.height - paddingBottom),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
        }

        // 2. 100% 積み上げバー描画（下：脂質 ➔ 中：炭水化物 ➔ 上：タンパク質）
        for (i in 0 until totalBars) {
            val s = summaries[i]
            val x = paddingLeft + (i * step) + (step / 2f)
            val left = x - (barWidth / 2f)

            if (selectedIndex == i) {
                drawRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(paddingLeft + (i * step), paddingTop),
                    size = Size(step, chartHeight)
                )
            }

            val pCal = s.dietaryProteinGrams * 4.0
            val fCal = s.dietaryFatGrams * 9.0
            val cCal = s.dietaryCarbsGrams * 4.0
            val totalCal = pCal + fCal + cCal

            if (totalCal > 0) {
                val fRatio = (fCal / totalCal).toFloat()
                val cRatio = (cCal / totalCal).toFloat()
                val pRatio = (pCal / totalCal).toFloat()

                val fH = fRatio * chartHeight
                val cH = cRatio * chartHeight
                val pH = pRatio * chartHeight

                val baseY = paddingTop + chartHeight

                // 1. 下層: 脂質 (F)
                drawRect(
                    color = ColorFat,
                    topLeft = Offset(left, baseY - fH),
                    size = Size(barWidth, fH)
                )

                // 2. 中間層: 炭水化物 (C)
                drawRect(
                    color = ColorCarbs,
                    topLeft = Offset(left, baseY - fH - cH),
                    size = Size(barWidth, cH)
                )

                // 3. 最上層: タンパク質 (P)
                drawRect(
                    color = ColorProtein,
                    topLeft = Offset(left, baseY - fH - cH - pH),
                    size = Size(barWidth, pH)
                )
            } else {
                // 食事データなしの日は薄いプレースホルダーバー
                val baseY = paddingTop + chartHeight
                drawRect(
                    color = Color.White.copy(alpha = 0.05f),
                    topLeft = Offset(left, paddingTop),
                    size = Size(barWidth, chartHeight)
                )
            }
        }

        // 3. X軸日付ラベル描画
        val datePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val monthPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#38BDF8")
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val yPos = size.height - 6.dp.toPx()

        for (i in 0 until totalBars) {
            val date = parseLocalDate(summaries[i].date) ?: continue
            val x = paddingLeft + (i * step) + (step / 2f)

            if (date.dayOfMonth == 1) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${date.monthValue}/1",
                    x,
                    yPos,
                    monthPaint
                )
            } else if (i == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${date.monthValue}/${date.dayOfMonth}",
                    x,
                    yPos,
                    datePaint
                )
            } else if (i == totalBars - 1) {
                // 最右端
                drawContext.canvas.nativeCanvas.drawText(
                    "${date.dayOfMonth}",
                    x,
                    yPos,
                    datePaint
                )
            } else if (i % 6 == 0 && (i + 1) < totalBars - 2 && date.dayOfMonth != 1) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${date.dayOfMonth}",
                    x,
                    yPos,
                    datePaint
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 補助ユーティリティ
// -------------------------------------------------------------
@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun parseLocalDate(dateStr: String): LocalDate? {
    return try {
        LocalDate.parse(dateStr)
    } catch (e: Exception) {
        null
    }
}

private fun formatFullDate(dateStr: String): String {
    return try {
        val d = LocalDate.parse(dateStr)
        d.format(DateTimeFormatter.ofPattern("M月d日 (E)"))
    } catch (e: Exception) {
        dateStr
    }
}
