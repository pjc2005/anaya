package com.anaya.app.presentation.settings
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    viewModel: SettingsViewModel = hiltViewModel()
) {
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
                icon = Icons.Default.Download,
                title = "导入/导出",
                subtitle = "导出为 JSON 或 Excel 文件，或从文件恢复数据",
                onClick = onNavigateToExportImport
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            SettingsItem(
                icon = Icons.Default.DarkMode,
                title = "深色模式",
                subtitle = "跟随系统设置",
                onClick = { }
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
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
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
            Text(title, style = MaterialTheme.typography.bodyLarge)
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
