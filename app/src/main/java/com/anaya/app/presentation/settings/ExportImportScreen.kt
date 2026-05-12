package com.anaya.app.presentation.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anaya.app.data.export.ExportFormat
import com.anaya.app.data.export.ExportResult
import com.anaya.app.data.export.ImportResult
import com.anaya.app.data.export.SmartImportPreview
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ExportImportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 导出文件选择器
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.doExport(uri)
        }
    }

    // 导入文件选择器（标准格式）
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.doImport(uri)
        }
    }

    // 智能导入文件选择器（任意格式）
    val smartImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.doSmartImport(uri)
        }
    }

    // 处理结果展示（导出）
    LaunchedEffect(state.exportResult) {
        state.exportResult?.let { result ->
            val msg = if (result.success) "✅ ${result.message}" else "❌ ${result.message}"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearExportResult()
        }
    }
    // 处理结果展示（导入）
    LaunchedEffect(state.importResult) {
        state.importResult?.let { result ->
            val msg = if (result.success) "✅ ${result.message}" else "❌ ${result.message}"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearImportResult()
        }
    }
    // 处理结果展示（智能导入）
    LaunchedEffect(state.smartImportResult) {
        state.smartImportResult?.let { result ->
            val msg = if (result.success) "✅ ${result.message}" else "❌ ${result.message}"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearSmartImportResult()
        }
    }

    // 智能导入预览弹窗
    state.smartPreview?.let { preview ->
        SmartImportPreviewDialog(
            preview = preview,
            onConfirm = { viewModel.confirmSmartImport() },
            onDismiss = { viewModel.cancelSmartImport() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入/导出") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ═══════════════ 导出区域 ═══════════════
            Text(
                text = "导出数据",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "将交易记录导出为文件，可备份或迁移到其他设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // 导出格式选择
            Text("导出格式：", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = state.selectedExportFormat == ExportFormat.JSON,
                    onClick = { viewModel.selectExportFormat(ExportFormat.JSON) },
                    label = { Text("JSON") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                FilterChip(
                    selected = state.selectedExportFormat == ExportFormat.EXCEL,
                    onClick = { viewModel.selectExportFormat(ExportFormat.EXCEL) },
                    label = { Text("Excel (.xlsx)") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // 导出统计
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "当前数据概览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.transactionCount >= 0) {
                        Text("交易记录：${state.transactionCount} 条")
                        Text("账户：${state.accountCount} 个")
                        Text("分类：${state.categoryCount} 个")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val suffix = when (state.selectedExportFormat) {
                        ExportFormat.JSON -> ".json"
                        ExportFormat.EXCEL -> ".xlsx"
                    }
                    exportLauncher.launch("anaya_export_${System.currentTimeMillis()}$suffix")
                },
                enabled = !state.isExporting && state.transactionCount > 0
            ) {
                if (state.isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("导出中...")
                } else {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导出到文件")
                }
            }

            if (state.transactionCount == 0) {
                Text(
                    text = "暂无交易记录可导出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ═══════════════ 导入区域 ═══════════════
            Text(
                text = "导入数据（标准格式）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "从 Anaya 导出的 JSON 或 Excel 文件恢复交易记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // 导入格式选择
            Text("导入格式：", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = state.selectedImportFormat == ExportFormat.JSON,
                    onClick = { viewModel.selectImportFormat(ExportFormat.JSON) },
                    label = { Text("JSON") },
                    leadingIcon = {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                FilterChip(
                    selected = state.selectedImportFormat == ExportFormat.EXCEL,
                    onClick = { viewModel.selectImportFormat(ExportFormat.EXCEL) },
                    label = { Text("Excel (.xlsx)") },
                    leadingIcon = {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // 导入注意事项
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "注意事项：",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("• 导入会新增交易记录，不会覆盖已有数据", style = MaterialTheme.typography.bodySmall)
                    Text("• Excel 导入需要表头格式匹配（日期、类型、金额、分类、账户、备注）", style = MaterialTheme.typography.bodySmall)
                    Text("• 金额单位统一为元", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val mimeTypes = when (state.selectedImportFormat) {
                        ExportFormat.JSON -> arrayOf("application/json", "text/plain")
                        ExportFormat.EXCEL -> arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/octet-stream"
                        )
                    }
                    importLauncher.launch(mimeTypes)
                },
                enabled = !state.isImporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("导入中...")
                } else {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("选择文件导入")
                }
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ═══════════════ 智能导入区域 ═══════════════
            Text(
                text = "智能导入（AI 识别）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "支持任意格式：支付宝/微信导出的 CSV、银行对账单、其他记账App的导出文件等",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // 智能导入说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "支持的格式：",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("• CSV / TSV / 文本分隔符文件 — 自动探测表头和列", style = MaterialTheme.typography.bodySmall)
                    Text("• 其他 App 导出文件 — 本地 AI 模型智能提取交易记录", style = MaterialTheme.typography.bodySmall)
                    Text("• 纯文本描述 — 正则匹配金额信息", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    smartImportLauncher.launch(arrayOf("*/*")) // 支持任意文件类型
                },
                enabled = !state.isSmartImporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                if (state.isSmartImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("AI 识别中...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("选择文件 AI 智能导入")
                }
            }

            Spacer(Modifier.height(24.dp))

            // 导入结果展示（标准导入）
            state.importResult?.let { result ->
                ImportResultCard(result)
                Spacer(Modifier.height(16.dp))
            }

            // 智能导入结果展示
            state.smartImportResult?.let { result ->
                ImportResultCard(result)
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportResultCard(result: ImportResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (result.success) "导入成功" else "导入失败",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(text = result.message, style = MaterialTheme.typography.bodySmall)
            if (result.importedCount > 0) {
                Text("已导入：${result.importedCount} 条", style = MaterialTheme.typography.bodySmall)
            }
            if (result.skippedCount > 0) {
                Text("跳过：${result.skippedCount} 条", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (result.errors.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                result.errors.take(3).forEach { err ->
                    Text("• $err", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (result.errors.size > 3) {
                    Text("...还有 ${result.errors.size - 3} 条错误",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartImportPreviewDialog(
    preview: SmartImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("智能导入预览")
            }
        },
        text = {
            Column {
                Text(
                    text = preview.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "共识别 ${preview.count} 条交易记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (preview.errors.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "⚠ ${preview.errors.size} 条解析异常",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (preview.count > 0) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(preview.transactions.take(50)) { index, tx ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${tx.amount / 100.0}元",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tx.type == "INCOME")
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = tx.note ?: "无备注",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = dateFormat.format(Date(tx.date)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (preview.count > 50) {
                            item {
                                Text(
                                    text = "...还有 ${preview.count - 50} 条未显示",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (preview.errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "无法解析的记录：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    preview.errors.take(3).forEach { err ->
                        Text(
                            "• $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = preview.count > 0
            ) {
                Text("确认导入 ${preview.count} 条")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
