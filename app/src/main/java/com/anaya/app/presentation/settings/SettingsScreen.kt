package com.anaya.app.presentation.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anaya.app.ml.ModelStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCategories: () -> Unit = {},
    onNavigateToAccounts: () -> Unit = {},
    onNavigateToSmartCapture: () -> Unit = {},
    onNavigateToExportImport: () -> Unit = {},
    onNavigateToSavings: () -> Unit = {},
    onNavigateToAutoCaptureLog: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteInput by remember { mutableStateOf("") }
    val isDeleting by viewModel.isDeletingAll.collectAsState()

    // Restore file picker
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreData(uri)
        }
    }

    // Handle backup/restore results
    LaunchedEffect(Unit) {
        viewModel.backupResult.collect { msg ->
            when {
                msg == "backup_started" -> {
                    Toast.makeText(context, "备份中...", Toast.LENGTH_SHORT).show()
                }
                msg.startsWith("backup_success:") -> {
                    val path = msg.removePrefix("backup_success:")
                    Toast.makeText(context, "备份成功：$path", Toast.LENGTH_LONG).show()
                }
                msg.startsWith("backup_error:") -> {
                    val err = msg.removePrefix("backup_error:")
                    Toast.makeText(context, "备份失败：$err", Toast.LENGTH_LONG).show()
                }
                msg == "restore_started" -> {
                    Toast.makeText(context, "恢复中...", Toast.LENGTH_SHORT).show()
                }
                msg.startsWith("restore_success:") -> {
                    val count = msg.removePrefix("restore_success:")
                    Toast.makeText(context, "恢复成功，已导入 $count 条记录", Toast.LENGTH_LONG).show()
                }
                msg.startsWith("restore_error:") -> {
                    val err = msg.removePrefix("restore_error:")
                    Toast.makeText(context, "恢复失败：$err", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Category,
                title = "分类管理",
                subtitle = "自定义收支分类",
                onClick = onNavigateToCategories
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            SettingsItem(
                icon = Icons.Default.AccountBalance,
                title = "账户管理",
                subtitle = "管理银行卡、支付宝等账户",
                onClick = onNavigateToAccounts
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            SettingsItem(
                icon = Icons.Default.Visibility,
                title = "智能捕获",
                subtitle = "剪贴板监听 & 自动识别支付信息",
                onClick = onNavigateToSmartCapture
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            SettingsItem(
                icon = Icons.Default.Info,
                title = "检测日志",
                subtitle = "查看支付检测的三层降级记录",
                onClick = onNavigateToAutoCaptureLog
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            SettingsItem(
                icon = Icons.Default.Download,
                title = "导入/导出",
                subtitle = "导出为 JSON 或 Excel 文件，或从文件恢复数据",
                onClick = onNavigateToExportImport
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            // 主题设置
            val currentTheme by viewModel.themeMode.collectAsState()
            val themeLabels = mapOf(
                com.anaya.app.presentation.theme.ThemeMode.SYSTEM to "跟随系统",
                com.anaya.app.presentation.theme.ThemeMode.LIGHT to "浅色",
                com.anaya.app.presentation.theme.ThemeMode.DARK to "深色"
            )
            SettingsItem(
                icon = Icons.Default.DarkMode,
                title = "主题",
                subtitle = themeLabels[currentTheme] ?: "跟随系统",
                onClick = { viewModel.cycleTheme() }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            // 备份数据
            SettingsItem(
                icon = Icons.Default.Backup,
                title = "备份数据",
                subtitle = "导出全部数据到 Download 目录",
                onClick = { viewModel.backupData() }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            // 恢复数据
            SettingsItem(
                icon = Icons.Default.RestorePage,
                title = "恢复数据",
                subtitle = "从备份文件恢复数据",
                onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            // 模型状态
            val modelStatus by viewModel.modelStatus.collectAsState()
            val downloadProgress by viewModel.downloadProgress.collectAsState()

            LaunchedEffect(Unit) { viewModel.checkModelStatus() }

            when (modelStatus) {
                ModelStatus.Ready -> SettingsItem(
                    icon = Icons.Default.Info,
                    title = "AI 模型已就绪",
                    subtitle = "本地智能识别引擎正常",
                    onClick = { }
                )
                ModelStatus.Downloading -> SettingsItem(
                    icon = Icons.Default.Download,
                    title = "正在下载模型...",
                    subtitle = "进度 $downloadProgress% （首次下载约需 10-15 分钟）",
                    onClick = { }
                )
                ModelStatus.NotDownloaded -> SettingsItem(
                    icon = Icons.Default.Download,
                    title = "下载 AI 模型",
                    subtitle = "下载后启用本地智能识别（469MB，仅一次）",
                    onClick = { viewModel.downloadModel() }
                )
                ModelStatus.Error -> SettingsItem(
                    icon = Icons.Default.Download,
                    title = "下载失败，点击重试",
                    subtitle = "请检查网络连接后重试",
                    onClick = { viewModel.downloadModel() }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            // 删除全部数据
            val isDeleting by viewModel.isDeletingAll.collectAsState()
            SettingsItem(
                icon = Icons.Default.Delete,
                title = "删除全部数据",
                subtitle = if (isDeleting) "删除中..." else "清空所有交易记录，账户余额恢复初始值",
                onClick = {
                    if (!isDeleting) {
                        showDeleteConfirm = true
                        deleteInput = ""
                    }
                },
                titleColor = MaterialTheme.colorScheme.error
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Anaya v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 删除确认弹窗
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = {
                    if (!isDeleting) {
                        showDeleteConfirm = false
                        deleteInput = ""
                    }
                },
                title = { Text("确认删除全部数据") },
                text = {
                    Column {
                        Text("此操作将删除所有交易记录，并将所有账户余额重置为初始值。此操作不可撤销！")
                        Spacer(Modifier.height(12.dp))
                        Text("请输入「确认删除」以继续：", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = deleteInput,
                            onValueChange = { deleteInput = it },
                            placeholder = { Text("确认删除") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteAllTransactions()
                            showDeleteConfirm = false
                            deleteInput = ""
                        },
                        enabled = deleteInput == "确认删除" && !isDeleting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("确认删除")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            deleteInput = ""
                        },
                        enabled = !isDeleting
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
