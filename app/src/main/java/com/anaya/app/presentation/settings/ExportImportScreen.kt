package com.anaya.app.presentation.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anaya.app.data.export.ExportFormat
import com.anaya.app.data.export.ExportResult
import com.anaya.app.data.export.ImportResult

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

    // 导入文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.doImport(uri)
        }
    }

    // 处理结果展示
    LaunchedEffect(state.exportResult) {
        state.exportResult?.let { result ->
            val msg = if (result.success) "✅ ${result.message}" else "❌ ${result.message}"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearExportResult()
        }
    }
    LaunchedEffect(state.importResult) {
        state.importResult?.let { result ->
            val msg = if (result.success) "✅ ${result.message}" else "❌ ${result.message}"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearImportResult()
        }
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
                text = "导入数据",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "从之前导出的文件恢复交易记录",
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

            Spacer(Modifier.height(24.dp))

            // 导入结果展示
            state.importResult?.let { result ->
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

            Spacer(Modifier.height(24.dp))
        }
    }
}
