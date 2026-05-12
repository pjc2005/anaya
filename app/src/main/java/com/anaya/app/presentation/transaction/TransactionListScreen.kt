package com.anaya.app.presentation.transaction
import com.anaya.app.util.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.util.*
import com.anaya.app.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    onTransactionClick: (Long) -> Unit = {},
    viewModel: TransactionListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.CHINA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单") },
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.groupedTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("该月暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                state.groupedTransactions.forEach { (dateLabel, items) ->
                    item {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    items(items, key = { it.id }) { tx ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clickable { onTransactionClick(tx.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tx.categoryIcon, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.categoryName, fontWeight = FontWeight.Medium)
                                    Row {
                                        Text(
                                            tx.note ?: dateFormat.format(Date(tx.date)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (tx.type == TransactionType.TRANSFER) {
                                            Spacer(Modifier.width(4.dp))
                                            Text("转账", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (tx.type == TransactionType.INCOME) "+¥%,.2f".format(tx.amount / 100.0)
                                        else "-¥%,.2f".format(tx.amount / 100.0),
                                        color = if (tx.type == TransactionType.INCOME)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (tx.type == TransactionType.TRANSFER) {
                                        Text(
                                            "¥%,.2f".format(tx.amount / 100.0),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        val dailyTotal = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } -
                                items.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                        if (dailyTotal != 0L) {
                            Text(
                                "当日合计: ${if (dailyTotal > 0) "-" else "+"}¥%,.2f".format(kotlin.math.abs(dailyTotal) / 100.0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
