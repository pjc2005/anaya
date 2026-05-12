package com.anaya.app.presentation.budget

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anaya.app.domain.model.BudgetPeriod
import com.anaya.app.domain.model.Category
import com.anaya.app.util.centsToDisplayString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预算管理") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "添加预算")
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.budgets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "暂无预算",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击右下角 + 添加预算",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total budget card at top
                val totalBudget = state.budgets.filter { it.budget.categoryId == null }
                if (totalBudget.isNotEmpty()) {
                    item {
                        TotalBudgetCard(totalBudget.first())
                    }
                }

                // Category budgets
                val catBudgets = state.budgets.filter { it.budget.categoryId != null }
                items(catBudgets, key = { it.budget.id }) { item ->
                    BudgetCard(
                        item = item,
                        onDelete = { viewModel.deleteBudget(item.budget) }
                    )
                }
            }
        }
    }

    // Add budget dialog
    if (state.showAddDialog) {
        BudgetAddDialog(
            categories = state.categories,
            amount = state.dialogAmount,
            period = state.dialogPeriod,
            threshold = state.dialogThreshold,
            selectedCategoryId = state.dialogCategoryId,
            onAmountChange = { viewModel.setDialogAmount(it) },
            onPeriodChange = { viewModel.setDialogPeriod(it) },
            onThresholdChange = { viewModel.setDialogThreshold(it) },
            onCategoryChange = { viewModel.setDialogCategoryId(it) },
            onConfirm = { viewModel.saveBudget() },
            onDismiss = { viewModel.dismissDialog() }
        )
    }
}

@Composable
private fun TotalBudgetCard(item: BudgetWithSpending) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "总预算",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            BudgetProgressBar(item = item, isLarge = true)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "已用 ¥${centsToDisplayString(item.spent)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "预算 ¥${centsToDisplayString(item.budget.amount)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun BudgetCard(
    item: BudgetWithSpending,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    MaterialTheme.colorScheme.errorContainer
                else Color.Transparent,
                label = "delete_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.categoryIcon,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        item.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        item.budget.period.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                BudgetProgressBar(item = item)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "已用 ¥${centsToDisplayString(item.spent)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.percentage >= item.budget.alertThreshold / 100f)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "预算 ¥${centsToDisplayString(item.budget.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetProgressBar(item: BudgetWithSpending, isLarge: Boolean = false) {
    val progressColor = when {
        item.percentage >= 1f -> MaterialTheme.colorScheme.error
        item.percentage >= item.budget.alertThreshold / 100f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column {
        LinearProgressIndicator(
            progress = { item.percentage.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLarge) 12.dp else 8.dp),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        if (item.percentage >= 1f) {
            Text(
                "超支 ${(item.percentage * 100 - 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                "剩余 ${((1f - item.percentage) * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetAddDialog(
    categories: List<Category>,
    amount: String,
    period: BudgetPeriod,
    threshold: String,
    selectedCategoryId: Long?,
    onAmountChange: (String) -> Unit,
    onPeriodChange: (BudgetPeriod) -> Unit,
    onThresholdChange: (String) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Category selector
                ExposedDropdownMenuBox(
                    expanded = showCategoryMenu,
                    onExpandedChange = { showCategoryMenu = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedCategoryId == null) "总预算" else
                            categories.find { it.id == selectedCategoryId }?.name ?: "总预算",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("📊 总预算") },
                            onClick = { onCategoryChange(null); showCategoryMenu = false }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.icon ?: "📦"} ${cat.name}") },
                                onClick = { onCategoryChange(cat.id); showCategoryMenu = false }
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) onAmountChange(it) },
                    label = { Text("预算金额 (元)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                // Period
                ExposedDropdownMenuBox(
                    expanded = showPeriodMenu,
                    onExpandedChange = { showPeriodMenu = it }
                ) {
                    OutlinedTextField(
                        value = when (period) {
                            BudgetPeriod.WEEKLY -> "每周"
                            BudgetPeriod.MONTHLY -> "每月"
                            BudgetPeriod.YEARLY -> "每年"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("周期") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPeriodMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showPeriodMenu,
                        onDismissRequest = { showPeriodMenu = false }
                    ) {
                        BudgetPeriod.entries.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (p) {
                                        BudgetPeriod.WEEKLY -> "每周"
                                        BudgetPeriod.MONTHLY -> "每月"
                                        BudgetPeriod.YEARLY -> "每年"
                                    })
                                },
                                onClick = { onPeriodChange(p); showPeriodMenu = false }
                            )
                        }
                    }
                }

                // Alert threshold
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { if (it.all { c -> c.isDigit() }) onThresholdChange(it) },
                    label = { Text("提醒阈值 (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
