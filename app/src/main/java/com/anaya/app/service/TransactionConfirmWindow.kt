package com.anaya.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.CategoryType

/**
 * 悬浮窗确认弹窗
 *
 * 检测到支付后悬浮显示，让用户确认/编辑后再保存。
 * 使用 WindowManager.TYPE_APPLICATION_OVERLAY 在应用上层绘制。
 */
class TransactionConfirmWindow(
    private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var isShowing = false
    private var lastDismissTime = 0L

    companion object {
        /** 关闭后冷却时间（2.5 秒内不可再次弹出） */
        private const val COOLDOWN_MS = 2500L
    }

    /** 检查系统悬浮窗权限 */
    fun canShowOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    /**
     * 显示确认弹窗
     *
     * @param initialAmount 检测到的金额（分）
     * @param categories     已有全部分类
     * @param accounts       已有全部账户
     * @param onSave         用户确认保存回调
     * @param onCancel       用户取消回调
     */
    fun show(
        initialAmount: Long,
        categories: List<Category>,
        accounts: List<Account>,
        initialNote: String? = null,
        onSave: (amount: Long, note: String?, type: String, accountId: Long, categoryId: Long?) -> Unit,
        onCancel: () -> Unit
    ) {
        if (isShowing) return
        if (System.currentTimeMillis() - lastDismissTime < COOLDOWN_MS) {
            // 冷却期内直接保存（不弹窗）
            onSave(initialAmount, initialNote, "EXPENSE", accounts.firstOrNull()?.id ?: 1L, null)
            return
        }
        if (!canShowOverlay()) {
            onSave(initialAmount, initialNote, "EXPENSE", accounts.firstOrNull()?.id ?: 1L, null)
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val composeView = ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    ConfirmContent(
                        initialAmount = initialAmount,
                        initialNote = initialNote,
                        categories = categories,
                        accounts = accounts,
                        onSave = { amt, note, type, acctId, catId ->
                            dismiss()
                            onSave(amt, note, type, acctId, catId)
                        },
                        onCancel = {
                            dismiss()
                            onCancel()
                        }
                    )
                }
            }
        }

        overlayView = FrameLayout(context).apply {
            addView(composeView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        windowManager.addView(overlayView!!, params)
        isShowing = true
    }

    /** 关闭弹窗 */
    fun dismiss() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) { }
        }
        overlayView = null
        isShowing = false
        lastDismissTime = System.currentTimeMillis()
    }

    /** 是否正在展示 */
    val showing: Boolean get() = isShowing
}

// ── Compose UI ──

@Composable
private fun ConfirmContent(
    initialAmount: Long,
    initialNote: String?,
    categories: List<Category>,
    accounts: List<Account>,
    onSave: (amount: Long, note: String?, type: String, accountId: Long, categoryId: Long?) -> Unit,
    onCancel: () -> Unit
) {
    val expenseCats = remember(categories) {
        val ec = categories.filter { it.type == CategoryType.EXPENSE }
        if (ec.isEmpty()) categories else ec
    }

    var amountText by remember { mutableStateOf("%.2f".format(initialAmount / 100.0)) }
    var isExpense by remember { mutableStateOf(true) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 1L) }
    var noteText by remember { mutableStateOf(initialNote ?: "") }
    var showAllCats by remember { mutableStateOf(false) }

    // 半透明背景 — 点击背景取消
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        // 居中卡片 — 拦截点击
        Card(
            modifier = Modifier
                .padding(24.dp)
                .clickable(enabled = false, onClick = { })
                .widthIn(max = 380.dp)
                .heightIn(max = 580.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ── 标题 ──
                Text("确认记账", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                // ── 金额 ──
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("¥") }
                )
                Spacer(Modifier.height(12.dp))

                // ── 类型切换（支出 / 收入） ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isExpense,
                        onClick = { isExpense = true },
                        label = { Text("支出") }
                    )
                    FilterChip(
                        selected = !isExpense,
                        onClick = { isExpense = false },
                        label = { Text("收入") }
                    )
                }
                Spacer(Modifier.height(12.dp))

                // ── 分类网格 ──
                Text("分类", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))

                val catsToShow = if (showAllCats) expenseCats else expenseCats.take(6)
                catsToShow.chunked(3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        row.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = {
                                    Text(
                                        "${cat.icon?.take(1) ?: ""} ${cat.name}",
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }
                    }
                }
                if (expenseCats.size > 6) {
                    TextButton(onClick = { showAllCats = !showAllCats }) {
                        Text(if (showAllCats) "收起" else "更多...")
                    }
                }

                // ── 其他（无分类时兜底选择） ──
                Spacer(Modifier.height(12.dp))

                // ── 账户选择 ──
                Text("账户", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = selectedAccountId == account.id,
                            onClick = { selectedAccountId = account.id },
                            label = { Text(account.name, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ── 备注 ──
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))

                // ── 按钮 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }

                    Button(
                        onClick = {
                            val finalAmount = ((amountText.toDoubleOrNull()
                                ?: (initialAmount / 100.0)) * 100).toLong()
                            onSave(
                                finalAmount,
                                noteText.ifBlank { null },
                                if (isExpense) "EXPENSE" else "INCOME",
                                selectedAccountId,
                                selectedCategoryId
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存") }
                }
            }
        }
    }
}
