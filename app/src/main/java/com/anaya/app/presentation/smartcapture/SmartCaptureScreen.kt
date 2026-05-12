package com.anaya.app.presentation.smartcapture

import com.anaya.app.util.CurrencyUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCaptureScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SmartCaptureViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能捕获") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.detectedList.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAll() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.detectedList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "暂无捕获记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "复制支付信息或打开支付页面后将自动检测",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.detectedList, key = { it.hashCode() }) { detected ->
                    DetectedCard(
                        detected = detected,
                        categories = state.categories,
                        accounts = state.accounts,
                        onAccept = { catId, acctId ->
                            viewModel.acceptTransaction(detected, catId, acctId)
                        },
                        onDismiss = { viewModel.dismissTransaction(detected) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectedCard(
    detected: DetectedTransaction,
    categories: List<com.anaya.app.domain.model.Category>,
    accounts: List<com.anaya.app.domain.model.Account>,
    onAccept: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var showAcceptDialog by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf(detected.suggestedCategoryId) }
    var selectedAccountId by remember { mutableStateOf(1L) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "¥${CurrencyUtils.centsToDisplayString(detected.parsed.amount ?: 0)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    detected.source.let {
                        when (it) { "clipboard" -> "剪贴板"; "accessibility" -> "自动"; "ocr" -> "OCR"; else -> it }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            detected.parsed.merchant?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            detected.parsed.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("忽略")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showAcceptDialog = true }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("记录")
                }
            }
        }
    }

    if (showAcceptDialog) {
        AlertDialog(
            onDismissRequest = { showAcceptDialog = false },
            title = { Text("确认记账") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val expenseCats = categories.filter { it.type == com.anaya.app.domain.model.CategoryType.EXPENSE }
                    Text("分类：", style = MaterialTheme.typography.labelMedium)
                    // Simple category selector
                    expenseCats.take(6).forEach { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id }
                            )
                            Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAccept(selectedCategoryId, selectedAccountId)
                    showAcceptDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAcceptDialog = false }) { Text("取消") }
            }
        )
    }
}
