package com.anaya.app.presentation.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                actions = {
                    Text(
                        "${state.selectedMonth.year}年${state.selectedMonth.monthValue}月",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { viewModel.previousMonth() }) { Text("<") }
                    TextButton(onClick = { viewModel.nextMonth() }) { Text(">") }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard("支出", state.totalExpense, colors.error, Modifier.weight(1f))
                    SummaryCard("收入", state.totalIncome, colors.primary, Modifier.weight(1f))
                    SummaryCard(
                        "结余",
                        state.totalIncome - state.totalExpense,
                        if (state.totalIncome >= state.totalExpense) colors.primary else colors.error,
                        Modifier.weight(1f)
                    )
                }

                if (state.categoryStats.isNotEmpty()) {
                    Text("支出分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PieChart(
                                stats = state.categoryStats,
                                modifier = Modifier.size(200.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            state.categoryStats.take(8).forEach { stat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(12.dp).padding(end = 8.dp)
                                    ) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            drawCircle(color = Color(getStatColor(stat.colorIndex)))
                                        }
                                    }
                                    Text(stat.categoryIcon, fontSize = 14.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stat.categoryName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "¥%,.2f".format(stat.amount / 100.0),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        " %.1f%%".format(stat.percentage * 100),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("本月无支出记录", color = colors.onSurfaceVariant)
                        }
                    }
                }

                if (state.dailyStats.any { it.expense > 0 || it.income > 0 }) {
                    Text("每日趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        LineChart(
                            dailyStats = state.dailyStats,
                            errorColor = colors.error,
                            modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(Modifier.height(4.dp))
            Text("¥%,.0f".format(amount / 100.0), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun PieChart(stats: List<CategoryStat>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val canvasSize = minOf(size.width, size.height)
        val radius = canvasSize / 2
        val center = Offset(size.width / 2, size.height / 2)
        var startAngle = -90f
        stats.forEach { stat ->
            drawArc(
                color = Color(getStatColor(stat.colorIndex)),
                startAngle = startAngle,
                sweepAngle = stat.percentage * 360f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(canvasSize, canvasSize)
            )
            startAngle += stat.percentage * 360f
        }
    }
}

@Composable
private fun LineChart(
    dailyStats: List<DailyStat>,
    errorColor: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = maxOf(dailyStats.maxOfOrNull { it.expense }?.toFloat() ?: 1f, 1f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 40f
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2

        for (i in 0..4) {
            val y = padding + chartHeight * (1 - i / 4f)
            drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(padding, y), Offset(width - padding, y), 1f)
        }

        if (dailyStats.isEmpty()) return@Canvas

        val path = Path()
        dailyStats.forEachIndexed { index, stat ->
            val x = padding + chartWidth * index / (dailyStats.size - 1).coerceAtLeast(1)
            val y = padding + chartHeight * (1 - stat.expense / maxValue)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, errorColor, style = Stroke(width = 2.5f))

        dailyStats.forEachIndexed { index, stat ->
            val x = padding + chartWidth * index / (dailyStats.size - 1).coerceAtLeast(1)
            val y = padding + chartHeight * (1 - stat.expense / maxValue)
            if (stat.expense > 0) drawCircle(errorColor, 3f, Offset(x, y))
        }
    }
}
