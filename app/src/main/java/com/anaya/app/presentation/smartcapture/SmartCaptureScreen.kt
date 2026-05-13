package com.anaya.app.presentation.smartcapture

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.util.CaptureLogManager
import com.anaya.app.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmartCaptureScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SmartCaptureViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.CHINA) }

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
                            Spacer(Modifier.width(4.dp))
                            Text("清空")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.detectedList.isEmpty()) {
            // ── 空状态 ──
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "暂无捕获记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "支付后将自动检测金额与商户",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支持：微信 / 支付宝 / 淘宝 / 饿了么 / 美团 / 京东 / 抖音 / 拼多多 / 云闪付",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // ── 检测列表 ──
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "共 ${state.detectedList.size} 条待处理",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(state.detectedList, key = { it.id }) { detected ->
                    DetectedCaptureCard(
                        detected = detected,
                        categories = state.categories,
                        dateFormat = dateFormat,
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

// ══════════════════════════════════════════════════════════════
//  检测卡片
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DetectedCaptureCard(
    detected: DetectedTransaction,
    categories: List<Category>,
    dateFormat: SimpleDateFormat,
    onAccept: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    // 确认动画状态
    var savingState by remember { mutableStateOf<ConfirmState>(ConfirmState.NONE) }
    val layerColor = CaptureLogManager.getLayerColor(detected.layer)

    // ── 分类推荐（前4个最相关的 + 1个"其他"） ──
    val expenseCats = remember(detected, categories) {
        categories.filter { it.type == com.anaya.app.domain.model.CategoryType.EXPENSE }
    }
    val suggestedCategoryIds = remember(detected, categories) {
        val catName = detected.parsed.merchant?.let { m ->
            com.anaya.app.ml.RuleBasedClassifier.suggestCategoryName(m, detected.parsed.note)
        }
        val matched = catName?.let { name ->
            categories.find { it.name == name }
        }
        val ids = mutableListOf<Long>()
        if (matched != null) ids.add(matched.id)
        // 常用分类补齐
        listOf("餐饮", "交通", "购物", "娱乐")
            .mapNotNull { cn -> categories.find { it.name == cn } }
            .forEach { if (it.id !in ids) ids.add(it.id) }
        ids.take(5)
    }

    // ── 自动保存倒计时 ──
    var countdown by remember { mutableStateOf(10) }
    var countdownActive by remember { mutableStateOf(true) }

    LaunchedEffect(detected.id, countdownActive) {
        if (countdownActive && countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        } else if (countdownActive && countdown == 0) {
            onAccept(suggestedCategoryIds.firstOrNull(), 1L)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (savingState == ConfirmState.NONE) {
                        onAccept(suggestedCategoryIds.firstOrNull(), 1L)
                        savingState = ConfirmState.SAVING
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── 顶部行：金额 + 来源标签 + 层级标识 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 金额
                Text(
                    "¥${CurrencyUtils.centsToDisplayString(detected.parsed.amount ?: 0)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // 来源标签
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(layerColor).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "L${detected.layer}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(layerColor)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            CaptureLogManager.getLayerLabel(detected.layer),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(layerColor)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // 来源标识
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        when (detected.source) {
                            "clipboard" -> "📋 剪贴板"
                            "accessibility" -> "🤖 自动"
                            "ocr" -> "📷 OCR"
                            else -> detected.source
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── 商户 + 时间行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                detected.parsed.merchant?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } ?: Text(
                    detected.parsed.note?.take(30) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    dateFormat.format(Date(detected.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 平台图标 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                val platformIcon = when {
                    detected.parsed.note?.contains("微信") == true -> "💬"
                    detected.parsed.note?.contains("支付宝") == true -> "🔵"
                    detected.parsed.note?.contains("淘宝") == true -> "🛒"
                    detected.parsed.note?.contains("饿了么") == true -> "🛵"
                    detected.parsed.note?.contains("美团") == true -> "🍔"
                    detected.parsed.note?.contains("京东") == true -> "🐶"
                    detected.parsed.note?.contains("抖音") == true -> "🎵"
                    detected.parsed.note?.contains("拼多多") == true -> "📦"
                    detected.parsed.note?.contains("云闪付") == true -> "💳"
                    else -> "💸"
                }
                Text(
                    "${platformIcon} ${detected.parsed.note?.take(30) ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── 推荐分类 (Chip 行) ──
            Text(
                "推荐分类",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                expenseCats.filter { it.id in suggestedCategoryIds }.forEach { cat ->
                    val isSuggested = cat.id == suggestedCategoryIds.firstOrNull()
                    SuggestionChip(
                        onClick = { onAccept(cat.id, 1L) },
                        label = {
                            Text(
                                "${getCategoryEmoji(cat.name)} ${cat.name}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isSuggested)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isSuggested)
                            SuggestionChipDefaults.suggestionChipBorder(
                                borderColor = MaterialTheme.colorScheme.primary,
                                enabled = true
                            ) else null
                    )
                }
                // 更多分类按钮
                SuggestionChip(
                    onClick = { /* TODO: 展开全部分类选择器 */ },
                    label = { Text("⋯ 更多", style = MaterialTheme.typography.labelMedium) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── 操作按钮 + 倒计时 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 倒计时提示
                if (countdownActive && countdown > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "$countdown",
                            modifier = Modifier.padding(6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "秒后自动记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(Modifier.weight(1f))

                // 忽略按钮
                OutlinedButton(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("忽略")
                }

                Spacer(Modifier.width(8.dp))

                // 确认按钮
                Button(
                    onClick = {
                        onAccept(suggestedCategoryIds.firstOrNull(), 1L)
                        savingState = ConfirmState.SAVING
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("记录")
                }
            }
        }
    }
}

enum class ConfirmState { NONE, SAVING }

/**
 * 获取分类名称对应的 Emoji
 */
private fun getCategoryEmoji(name: String): String = when {
    name.contains("餐饮") || name.contains("美食") || name.contains("外卖") -> "🍜"
    name.contains("交通") || name.contains("打车") || name.contains("公交") -> "🚗"
    name.contains("购物") || name.contains("日用") || name.contains("百货") -> "🛒"
    name.contains("娱乐") || name.contains("电影") || name.contains("游戏") -> "🎮"
    name.contains("居住") || name.contains("房租") || name.contains("水电") -> "🏠"
    name.contains("通讯") || name.contains("话费") || name.contains("手机") -> "📱"
    name.contains("医疗") || name.contains("看病") || name.contains("药") -> "🏥"
    name.contains("教育") || name.contains("学习") || name.contains("课程") -> "📚"
    name.contains("美容") || name.contains("健身") -> "💪"
    name.contains("工资") || name.contains("薪水") -> "💰"
    name.contains("红包") || name.contains("转账") -> "🧧"
    name.contains("退款") -> "↩️"
    name.contains("投资") || name.contains("理财") -> "📈"
    else -> "💸"
}
