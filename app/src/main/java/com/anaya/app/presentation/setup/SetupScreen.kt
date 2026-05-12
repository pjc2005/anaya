package com.anaya.app.presentation.setup

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anaya.app.ml.ModelStatus
import com.anaya.app.service.Platform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit = {},
    viewModel: SetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.isCompleting, state.currentStep) {
        if (state.currentStep == SetupStep.COMPLETE && state.isCompleting) {
            onSetupComplete()
        }
    }

    Scaffold(
        topBar = {
            if (state.currentStep != SetupStep.WELCOME && state.currentStep != SetupStep.COMPLETE) {
                TopAppBar(
                    title = { Text("设置向导") },
                    navigationIcon = { /* 不允许返回，强制完成设置 */ }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 步骤指示器（非欢迎/完成页显示）
            if (state.currentStep != SetupStep.WELCOME && state.currentStep != SetupStep.COMPLETE) {
                SetupStepIndicator(
                    currentStep = state.currentStep,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 主体内容
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state.currentStep) {
                        SetupStep.WELCOME -> WelcomeStep(
                            onNext = { viewModel.nextStep() }
                        )

                        SetupStep.ACCESSIBILITY -> AccessibilityStep(
                            isEnabled = state.accessibilityEnabled,
                            isChecking = state.isChecking,
                            onOpenSettings = { viewModel.openAccessibilitySettings() },
                            onRefresh = { viewModel.refreshAccessibilityStatus() },
                            onNext = { viewModel.nextStep() }
                        )

                        SetupStep.AUTO_START -> AutoStartStep(
                            isSupported = state.autoStartEnabled != null,
                            onOpenSettings = { viewModel.openAutoStartSettings() },
                            onSkip = { viewModel.skipAutoStart() },
                            onNext = { viewModel.nextStep() }
                        )

                        SetupStep.BATTERY -> BatteryStep(
                            isIgnored = state.batteryOptimizationIgnored,
                            onOpenSettings = { viewModel.openBatteryOptimizationSettings() },
                            onRefresh = { viewModel.refreshBatteryStatus() },
                            onMarkDone = { viewModel.markBatteryOptimizationDone() },
                            onNext = { viewModel.nextStep() }
                        )

                        SetupStep.MODEL -> ModelStep(
                            modelStatus = state.modelStatus,
                            downloadProgress = state.downloadProgress,
                            onDownload = { viewModel.downloadModel() },
                            onSkip = { viewModel.skipModel() },
                            onNext = { viewModel.nextStep() }
                        )

                        SetupStep.COMPLETE -> CompleteStep()
                    }
                }
            }

            // 底部操作按钮（仅完成页显示）
            if (state.currentStep == SetupStep.COMPLETE) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    onClick = { viewModel.completeSetup() },
                    enabled = !state.isCompleting
                ) {
                    if (state.isCompleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("开始使用 Anaya")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// 步骤指示器
// ═══════════════════════════════════════════════════

@Composable
private fun SetupStepIndicator(currentStep: SetupStep, modifier: Modifier = Modifier) {
    // 设置步骤（不含欢迎/完成）
    val allSteps = listOf(
        SetupStep.ACCESSIBILITY,
        SetupStep.AUTO_START,
        SetupStep.BATTERY,
        SetupStep.MODEL
    )

    val currentIndex = allSteps.indexOf(currentStep).let { if (it < 0) 0 else it }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        allSteps.forEachIndexed { index, step ->
            val isActive = index == currentIndex
            val isComplete = index < currentIndex

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(80.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = when {
                        isComplete || isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isComplete) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (isActive || isComplete) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (index < allSteps.size - 1) {
                HorizontalDivider(
                    modifier = Modifier
                        .width(20.dp)
                        .padding(bottom = 16.dp),
                    color = if (isComplete) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// 步骤 1: 欢迎
// ═══════════════════════════════════════════════════

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(60.dp))

        Text(
            text = "Anaya",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "智能记账助手",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                FeatureRow(icon = "📱", text = "自动识别 7 大平台支付信息，无需手动输入")
                Spacer(Modifier.height(12.dp))
                FeatureRow(icon = "🤖", text = "本地 AI 模型智能分类，不联网更安全")
                Spacer(Modifier.height(12.dp))
                FeatureRow(icon = "📊", text = "月度统计、预算管理、省钱建议")
                Spacer(Modifier.height(12.dp))
                FeatureRow(icon = "🔒", text = "完全离线运行，数据不出手机")
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            onClick = onNext
        ) {
            Text("开始设置", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ═══════════════════════════════════════════════════
// 步骤 2: 无障碍服务（参考自动记账的引导风格）
// ═══════════════════════════════════════════════════

@Composable
private fun AccessibilityStep(
    isEnabled: Boolean,
    isChecking: Boolean,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.SettingsAccessibility,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
            tint = if (isEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "无障碍服务",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "开启后 Anaya 可自动识别微信、支付宝等支付应用的支付页面，无需手动复制粘贴。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // 状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isEnabled) "无障碍服务已开启"
                    else "尚未开启无障碍服务",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 操作说明
        if (!isEnabled) {
            Text(
                text = "操作步骤：",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            StepGuide(number = "1", text = "点击下方按钮打开系统设置")
            StepGuide(number = "2", text = "找到「已安装的服务」→「Anaya」")
            StepGuide(number = "3", text = "开启 Anaya 无障碍开关并确认")
            StepGuide(number = "4", text = "返回此页面，点击「检查状态」")

            Spacer(Modifier.height(24.dp))
        }

        // 按钮组
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenSettings
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("打开无障碍设置")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRefresh,
            enabled = !isChecking
        ) {
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("检查状态")
        }

        Spacer(Modifier.height(16.dp))

        // ── 平台识别说明（参考自动记账截图风格） ──
        if (!isEnabled) {
            Text(
                text = "支持的平台识别方式：",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Anaya 会自动识别以下平台的支付信息",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        PlatformGuideList()

        Spacer(Modifier.height(16.dp))

        Text(
            text = "无障碍服务不会读取您的个人信息，仅用于检测支付成功页面的文字信息。",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // 下一步按钮
        if (isEnabled) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNext
            ) {
                Text("下一步")
            }
        } else {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNext
            ) {
                Text("跳过此步（后面可在设置中开启）")
            }
        }
    }
}

// ── 平台识别说明列表（参考自动记账截图） ──

@Composable
private fun PlatformGuideList() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Platform.guideEntries.forEach { (platform, instruction) ->
            PlatformGuideCard(platform = platform, instruction = instruction)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PlatformGuideCard(platform: String, instruction: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = platform.take(1),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platform,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepGuide(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

// ═══════════════════════════════════════════════════
// 步骤 3: 自启动（参考自动记账：自启动(必须)）
// ═══════════════════════════════════════════════════

@Composable
private fun AutoStartStep(
    isSupported: Boolean,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "自启动（必须）",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "开启自启动可提升无障碍稳定性，请务必开启。部分手机（小米、华为等）默认会阻止应用后台启动。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        if (isSupported) {
            // 操作指南
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "建议开启，否则系统可能杀掉后台进程导致无法自动识别",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "操作步骤：",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            StepGuide(number = "1", text = "点击下方按钮打开自启动设置")
            StepGuide(number = "2", text = "找到「Anaya」应用")
            StepGuide(number = "3", text = "开启自启动开关")
            StepGuide(number = "4", text = "返回此页面，点击「下一步」")

            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSettings
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("打开自启动设置")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSkip
            ) {
                Text("此手机不需要自启动设置")
            }

            Spacer(Modifier.height(12.dp))

            // 设备品牌提示
            Text(
                text = "提示：不同品牌的手机设置路径不同（设置 → 应用管理 → 自启动）",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 厂商不支持此功能
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "当前手机无需额外设置自启动",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNext
            ) {
                Text("下一步")
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// 步骤 4: 忽略电池优化（参考自动记账：忽略电池优化(必须)）
// ═══════════════════════════════════════════════════

@Composable
private fun BatteryStep(
    isIgnored: Boolean,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onMarkDone: () -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.BatteryFull,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
            tint = if (isIgnored) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "忽略电池优化（必须）",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "防止系统在后台休眠时杀死 Anaya，确保无障碍服务持续运行。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // 状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isIgnored) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isIgnored) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isIgnored) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isIgnored) "已忽略电池优化"
                    else "尚未设置忽略电池优化",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (!isIgnored) {
            Spacer(Modifier.height(20.dp))

            Text(
                text = "操作步骤：",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            StepGuide(number = "1", text = "点击下方按钮打开系统请求")
            StepGuide(number = "2", text = "在弹出的对话框中选择「允许」")
            StepGuide(number = "3", text = "返回此页面，点击「检查状态」")

            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSettings
            ) {
                Icon(Icons.Default.BatteryFull, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("请求忽略电池优化")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRefresh
            ) {
                Text("检查状态")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onMarkDone
            ) {
                Text("已开启（手动跳过）")
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "提示：如果系统未弹出对话框，可稍后通过「系统设置 → 应用 → 特殊权限 → 忽略电池优化」手动开启。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNext
            ) {
                Text("下一步")
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// 步骤 5: AI 模型
// ═══════════════════════════════════════════════════

@Composable
private fun ModelStep(
    modelStatus: ModelStatus,
    downloadProgress: Int,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "AI 智能识别模型",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "下载后 Anaya 可在本地使用 AI 模型进行支付信息识别和智能分类，无需联网。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // 模型大小信息
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("模型信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("模型：Qwen2.5-0.5B (量化 Q4)")
                Text("大小：约 469 MB")
                Text("功能：支付解析 + 智能分类 + 省钱建议")
            }
        }

        Spacer(Modifier.height(24.dp))

        // 下载状态
        when (modelStatus) {
            ModelStatus.Ready -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("模型已下载就绪", style = MaterialTheme.typography.titleSmall)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = onNext) {
                    Text("下一步")
                }
            }
            ModelStatus.Downloading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("正在下载...", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "$downloadProgress%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            ModelStatus.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("下载失败", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重试下载")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onSkip) {
                    Text("跳过（之后在设置页下载）")
                }
            }
            ModelStatus.NotDownloaded -> {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("下载模型（469 MB）")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onSkip) {
                    Text("跳过（之后在设置页下载）")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "模型下载后完全离线运行，不会向网络发送任何数据。不下载模型时使用规则引擎回退，不影响基础记账功能。",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════
// 步骤 6: 完成
// ═══════════════════════════════════════════════════

@Composable
private fun CompleteStep() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "设置完成！",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "现在可以开始使用 Anaya 记账了",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
