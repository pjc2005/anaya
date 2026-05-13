package com.anaya.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.anaya.app.util.CaptureLogManager
import com.anaya.app.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCaptureLogScreen(
    onNavigateBack: () -> Unit = {}
) {
    // 每秒刷新
    var refreshTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            refreshTick = System.currentTimeMillis()
        }
    }

    val logs = remember(refreshTick) { CaptureLogManager.recentLogs }
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.CHINA) }
    val dateFormatToday = remember { SimpleDateFormat("HH:mm:ss", Locale.CHINA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("检测日志") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = { CaptureLogManager.clear() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                            Spacer(Modifier.width(4.dp))
                            Text("清空")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.HistoryToggleOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "暂无检测记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "在支持的 App 中完成支付后，记录会自动出现在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 统计头部
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "共 ${logs.size} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${logs.count { it.status == CaptureLogManager.Status.SAVED }} 已记账 · " +
                                    "${logs.count { it.status == CaptureLogManager.Status.PENDING }} 待处理",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                items(logs, key = { it.id }) { entry ->
                    LogEntryCard(
                        entry = entry,
                        dateFormat = dateFormat,
                        dateFormatToday = dateFormatToday
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(
    entry: CaptureLogManager.LogEntry,
    dateFormat: SimpleDateFormat,
    dateFormatToday: SimpleDateFormat
) {
    val isToday = entry.timestamp.let { ts ->
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        cal.timeInMillis = ts
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    }
    val timeStr = if (isToday) dateFormatToday.format(Date(entry.timestamp))
    else dateFormat.format(Date(entry.timestamp))

    val layerColor = Color(CaptureLogManager.getLayerColor(entry.layer))
    val statusColor = when (entry.status) {
        CaptureLogManager.Status.SAVED -> Color(0xFF34C759)
        CaptureLogManager.Status.PENDING -> Color(0xFFFF9500)
        CaptureLogManager.Status.DISMISSED -> Color(0xFF8E8E93)
        CaptureLogManager.Status.FAILED -> Color(0xFFFF3B30)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 层级标识色条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(layerColor)
            )

            Spacer(Modifier.width(12.dp))

            // 主内容
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.platform,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = layerColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "L${entry.layer}",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = layerColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "¥${CurrencyUtils.centsToDisplayString(entry.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    entry.merchant?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${CaptureLogManager.getLayerLabel(entry.layer)} · 置信度 ${(entry.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // 状态
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    entry.status.label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
