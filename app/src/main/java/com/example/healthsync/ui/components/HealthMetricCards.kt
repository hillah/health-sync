package com.example.healthsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthsync.data.model.HealthSummary
import com.example.healthsync.ui.theme.EmeraldGreen

@Composable
fun SummaryMetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    extraContent: (@Composable () -> Unit)? = null
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (extraContent != null) {
                extraContent()
            }
        }
    }
}

@Composable
fun SleepStagesBar(
    deepMin: Long,
    lightMin: Long,
    remMin: Long,
    awakeMin: Long,
    modifier: Modifier = Modifier
) {
    val total = (deepMin + lightMin + remMin + awakeMin).coerceAtLeast(1L).toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            if (deepMin > 0) {
                Box(
                    modifier = Modifier
                        .weight(deepMin / total)
                        .fillMaxHeight()
                        .background(Color(0xFF1E3A8A))
                )
            }
            if (lightMin > 0) {
                Box(
                    modifier = Modifier
                        .weight(lightMin / total)
                        .fillMaxHeight()
                        .background(Color(0xFF3B82F6))
                )
            }
            if (remMin > 0) {
                Box(
                    modifier = Modifier
                        .weight(remMin / total)
                        .fillMaxHeight()
                        .background(Color(0xFF8B5CF6))
                )
            }
            if (awakeMin > 0) {
                Box(
                    modifier = Modifier
                        .weight(awakeMin / total)
                        .fillMaxHeight()
                        .background(Color(0xFFF59E0B))
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("深い: ${deepMin}m", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E3A8A))
            Text("浅い: ${lightMin}m", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3B82F6))
            Text("レム: ${remMin}m", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B5CF6))
            Text("覚醒: ${awakeMin}m", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B))
        }
    }
}
