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
                    when (state.timePeriod) {
                        TimePeriod.MONTH -> {
                            Text(
                                "${state.selectedYearMonth.year}年${state.selectedYearMonth.monthValue}月",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { viewModel.previousPeriod() }) { Text("<") }
                            TextButton(onClick = { viewModel.nextPeriod() }) { Text(">") }
                        }
                        TimePeriod.QUARTER -> {
                            Text(
                                "${state.selectedYear}年 Q${state.selectedQuarter}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { viewModel.previousPeriod() }) { Text("<") }
                            TextButton(onClick = { viewModel.nextPeriod() }) { Text(">") }
                        }
                        TimePeriod.YEAR -> {
                            Text(
                                "${state.selectedYear}年",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { viewModel.previousPeriod() }) { Text("<") }
                            TextButton(onClick = { viewModel.nextPeriod() }) { Text(">") }
                        }
                        TimePeriod.ALL -> {
                            Text("总计", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
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
                // Period selector
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TimePeriod.entries.forEach { period ->
                        SegmentedButton(
                            selected = state.timePeriod == period,
                            onClick = { viewModel.setTimePeriod(period) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = TimePeriod.entries.indexOf(period),
                                count = TimePeriod.entries.size
                            )
                        ) {
                            Text(
                                when (period) {
                                    TimePeriod.MONTH -> "按月"
                                    TimePeriod.QUARTER -> "按季度"
                                    TimePeriod.YEAR -> "按年"
                                    TimePeriod.ALL -> "总计"
                                },
                                maxLines = 1
                            )
                        }
                    }
                }

                // Summary cards
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

                // Category breakdown
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
                            Text("该时段无支出记录", color = colors.onSurfaceVariant)
                        }
                    }
                }

                // Trend chart
                val hasData = state.dailyStats.any { it.expense > 0 || it.income > 0 }
                if (hasData) {
                    Text(
                        when (state.timePeriod) {
                            TimePeriod.MONTH -> "每日趋势"
                            TimePeriod.QUARTER -> "月度趋势"
                            TimePeriod.YEAR -> "月度趋势"
                            TimePeriod.ALL -> "月度趋势"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        val xLabels = state.dailyStats.map { stat ->
                            when (state.timePeriod) {
                                TimePeriod.MONTH -> "${stat.dayOfMonth}日"
                                TimePeriod.QUARTER -> "${stat.dayOfMonth}月" // months displayed here
                                TimePeriod.YEAR -> "${stat.dayOfMonth}月"
                                TimePeriod.ALL -> "${stat.dayOfMonth}月"
                            }
                        }
                        BarChart(
                            stats = state.dailyStats,
                            errorColor = colors.error,
                            primaryColor = colors.primary,
                            labels = xLabels,
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
private fun BarChart(
    stats: List<DailyStat>,
    errorColor: Color,
    primaryColor: Color,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val maxValue = maxOf(
        stats.maxOfOrNull { it.expense }?.toFloat() ?: 1f,
        stats.maxOfOrNull { it.income }?.toFloat() ?: 1f,
        1f
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val paddingBottom = 40f
        val paddingTop = 10f
        val paddingLeft = 10f
        val paddingRight = 10f
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        val barCount = stats.size.coerceAtLeast(1)
        val barWidth = (chartWidth / barCount) * 0.6f
        val gap = (chartWidth / barCount) * 0.4f

        stats.forEachIndexed { index, stat ->
            val x = paddingLeft + index * (barWidth + gap) + gap / 2
            val expenseHeight = if (maxValue > 0) (stat.expense / maxValue) * chartHeight else 0f
            val incomeHeight = if (maxValue > 0) (stat.income / maxValue) * chartHeight else 0f

            // Expense bar (negative = going down)
            if (expenseHeight > 0) {
                drawRect(
                    color = errorColor,
                    topLeft = Offset(x, paddingTop + chartHeight - expenseHeight),
                    size = Size(barWidth / 2, expenseHeight)
                )
            }
            // Income bar (positive = going up)
            if (incomeHeight > 0) {
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(x + barWidth / 2, paddingTop + chartHeight - incomeHeight),
                    size = Size(barWidth / 2, incomeHeight)
                )
            }
        }
    }
}
