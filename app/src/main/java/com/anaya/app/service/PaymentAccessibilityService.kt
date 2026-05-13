package com.anaya.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.RuleBasedParser
import com.anaya.app.util.CaptureLogManager
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 支付检测服务 — 规则引擎版
 *
 * 核心逻辑：
 * 1. 收到支付 App 窗口变化 → 延迟 300ms 等待页面渲染完成
 * 2. 获取 rootInActiveWindow → 遍历节点树 → 规则引擎匹配
 * 3. 匹配成功后 → 内容指纹去重 → 弹出悬浮确认窗或自动记账
 *
 * 规则定义见 CaptureEngine.kt，参考 GKD 设计。
 */
@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {

    @Inject lateinit var localModel: LocalModelInterface
    @Inject lateinit var eventBus: PaymentEventBus
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var ocrProcessor: OCRProcessor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var screenshotFallback: ScreenshotFallback

    private val FOREGROUND_NOTIFY_ID = 1001

    // ── 延迟扫描 ──
    private var pendingPkg: String? = null
    private var pendingEventText: String = ""
    private var pendingEventDesc: String = ""
    private val scanRunnable = Runnable { performScan() }

    // ── 内容指纹（防重复） ──
    private var lastFingerprint: String? = null
    private var lastFingerprintTime = 0L
    private var lastFingerprintPackage: String? = null
    private val FINGERPRINT_COOLDOWN = 5 * 60 * 1000L

    // ── 悬浮确认窗 ──
    private lateinit var confirmWindow: TransactionConfirmWindow

    // ── 保活悬浮窗（1px） ──
    private var keepAliveView: View? = null

    // ── 剪贴板监听（国产 ROM 上 rootInActiveWindow 不可用时的重要降级） ──
    private var clipboardText: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        confirmWindow = TransactionConfirmWindow(this)
        screenshotFallback = ScreenshotFallback(this)
        Log.i(TAG, "Service created, screenshotFallback.available=${screenshotFallback.isAvailable}")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val fgNotification = NotificationCompat.Builder(this, "payment_detect")
            .setContentTitle("Anaya 支付检测运行中")
            .setContentText("自动识别微信、支付宝等支付信息")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(FOREGROUND_NOTIFY_ID, fgNotification)
        createKeepAliveOverlay()

        serviceInfo.apply {
            // 不覆盖 eventTypes — 保留 XML 配置的 typeWindowStateChanged|typeWindowContentChanged
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            packageNames = arrayOf(
                "com.tencent.mm",
                "com.eg.android.AlipayGphone",
                "com.unionpay",
                "com.jingdong.app.mall",
                "com.sankuai.meituan",
                "com.ss.android.ugc.aweme",
                "com.xunmeng.pinduoduo"
            )
        }
        Log.i(TAG, "Service connected (foreground + keepalive + clipboard)")

        // 注册剪贴板监听
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.addPrimaryClipChangedListener(clipboardListener)
            Log.d(TAG, "Clipboard listener registered")
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard listener registration failed", e)
        }
    }

    override fun onDestroy() {
        removeKeepAliveOverlay()
        mainHandler.removeCallbacks(scanRunnable)
        confirmWindow.dismiss()
        // 移除剪贴板监听
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.removePrimaryClipChangedListener(clipboardListener)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 处理窗口切换 AND 内容变化事件（Tally 也用这两种）
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        val platform = Platform.fromPackage(pkg) ?: return

        // TYPE_WINDOW_CONTENT_CHANGED 频率高，只处理页面内容变化
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.contentChangeTypes != AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) return

        Log.d(TAG, "Window change: ${platform.label}")

        // 保存 event 数据供延迟扫描使用
        pendingPkg = pkg
        pendingEventText = event.text.joinToString(" ")
        pendingEventDesc = event.contentDescription?.toString().orEmpty()

        // 延迟 300ms 让页面渲染完（国产 ROM 上 rootInActiveWindow 在切换瞬间为 null）
        mainHandler.removeCallbacks(scanRunnable)
        mainHandler.postDelayed(scanRunnable, 300)
    }

    /**
     * 延迟后执行的实际扫描
     *
     * 三层降级链路：
     *   Layer 1: 无障碍节点树 → 规则引擎 (rootInActiveWindow + CaptureEngine)
     *   Layer 2: 事件文本 → RuleBasedParser (国产ROM节点为null时)
     *   Layer 3: 截图 → OCR → RuleBasedParser (前两层都失效时)
     */
    private fun performScan() {
        val pkg = pendingPkg ?: return
        val platform = Platform.fromPackage(pkg) ?: return

        // Layer 1: 无障碍节点树 + 规则引擎
        val root = rootInActiveWindow
        if (root != null) {
            Log.d(TAG, "${platform.label}: layer1 scanning with rule engine...")
            try {
                val result = CaptureEngine.runRules(root, pkg)
                if (result != null) {
                    scope.launch { onPaymentDetected(platform, result) }
                    return
                }
                Log.d(TAG, "${platform.label}: layer1 no rule matched, trying layer2...")
            } catch (e: Exception) {
                Log.e(TAG, "${platform.label} engine error", e)
            } finally {
                try {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        @Suppress("DEPRECATION")
                        root.recycle()
                    }
                } catch (_: Exception) {}
            }
        } else {
            Log.w(TAG, "${platform.label}: layer1 rootInActiveWindow is null")
        }

        // Layer 2: 事件文本降级 — LLM 解析 + 规则回退
        val eventText = "$pendingEventText $pendingEventDesc".trim()
        if (eventText.isNotBlank()) {
            scope.launch {
                // LLM 优先
                val parsed = localModel.parsePaymentText(eventText)
                if (parsed.amount != null && parsed.amount > 0 && parsed.confidence >= 0.5f) {
                    Log.i(TAG, "${platform.label}: layer2 LLM parsed amount=${parsed.amount / 100.0}")
                    CaptureLogManager.log(
                        platform = platform.label, amount = parsed.amount,
                        merchant = parsed.merchant, layer = 2, source = "llm-parse",
                        confidence = parsed.confidence
                    )
                    autoSaveFallback(platform, parsed.amount, parsed.merchant)
                    return@launch
                }
                // LLM 失败 → 规则回退
                val ruleParsed = withContext(Dispatchers.Default) {
                    RuleBasedParser.parsePaymentText(eventText)
                }
                if (ruleParsed.amount != null && ruleParsed.amount > 0 && ruleParsed.confidence >= 0.5f) {
                    Log.i(TAG, "${platform.label}: layer2 fallback parsed amount=${ruleParsed.amount / 100.0}")
                    CaptureLogManager.log(
                        platform = platform.label, amount = ruleParsed.amount,
                        merchant = ruleParsed.merchant, layer = 2, source = "accessibility",
                        confidence = ruleParsed.confidence
                    )
                    autoSaveFallback(platform, ruleParsed.amount, ruleParsed.merchant)
                }
            }
            // 继续 Layer 3 — 与 LLM 并行执行，由指纹去重兜底
        }

        // Layer 3: 截图 → OCR 兜底（适用于国产ROM上节点树和事件文本都拿不到金额的情况）
        if (screenshotFallback.isAvailable) {
            Log.d(TAG, "${platform.label}: trying layer3 screenshot OCR...")
            scope.launch {
                val ocrResult = screenshotFallback.captureAndOcr(ocrProcessor)
                if (ocrResult != null && ocrResult.amount != null && ocrResult.amount > 0) {
                    Log.i(TAG, "${platform.label}: layer3 OCR matched amount=${ocrResult.amount / 100.0}")
                    CaptureLogManager.log(
                        platform = platform.label, amount = ocrResult.amount,
                        merchant = ocrResult.merchant, layer = 3, source = "ocr",
                        confidence = ocrResult.confidence
                    )
                    autoSaveFallback(platform, ocrResult.amount, ocrResult.merchant)
                } else {
                    Log.d(TAG, "${platform.label}: all 3 layers failed -- no payment detected")
                }
            }
        } else {
            Log.d(TAG, "${platform.label}: OCR screenshot not available on this device")
        }
    }

    /**
     * 降级自动保存（无弹窗，使用智能分类）
     */
    private suspend fun autoSaveFallback(platform: Platform, amount: Long, merchant: String?) {
        if (isDuplicate(platform.packageName, amount, "EXPENSE", merchant ?: "")) return

        val cats = categoryRepository.getAllCategories().first()
        val catNames = cats.map { it.name }
        val classification = localModel.classifyTransaction(
            merchant = merchant,
            note = null,
            amount = amount,
            existingCategories = catNames
        )
        val categoryId = if (classification.confidence >= 0.4f && classification.explanation != null) {
            cats.find { it.name == classification.explanation }?.id ?: 0
        } else 0

        transactionRepository.insert(
            Transaction(
                amount = amount,
                type = TransactionType.EXPENSE,
                categoryId = categoryId,
                accountId = 1,
                note = merchant ?: platform.label,
                date = System.currentTimeMillis()
            )
        )
        showNotification(
            "已自动记账 [${platform.label}]",
            "${merchant ?: ""} ${"%.2f元".format(amount / 100.0)}"
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  公共处理管道
    // ═══════════════════════════════════════════════════════════════

    private suspend fun onPaymentDetected(
        platform: Platform,
        result: RuleMatchResult
    ) {
        val amount = result.amount

        // 内容指纹去重
        if (isDuplicate(platform.packageName, amount, result.rule.type, amount.toString())) {
            Log.d(TAG, "${platform.label}: duplicate fingerprint, skipped")
            return
        }

        Log.i(TAG, "Payment detected: ${platform.label} ${result.merchant ?: ""} ${amount / 100.0}元 (rule: ${result.rule.name})")

        CaptureLogManager.log(
            platform = platform.label, amount = amount,
            merchant = result.merchant, layer = 1, source = "accessibility",
            confidence = result.rule.confidence
        )

        // 加载数据（在 IO 线程）
        val categories = categoryRepository.getAllCategories().first()
        val accounts = accountRepository.getAllAccounts().first()

        // 切换到主线程操作 UI（confirmWindow.show 会创建 ViewRootImpl，必须在主线程）
        withContext(Dispatchers.Main) {
            if (confirmWindow.canShowOverlay()) {
                confirmWindow.show(
                    initialAmount = amount,
                    categories = categories,
                    accounts = accounts,
                    initialNote = result.merchant ?: platform.label,
                    onSave = { confirmedAmount, note, typeStr, accountId, categoryId ->
                        scope.launch {
                            saveTransaction(
                                platform = platform,
                                amount = confirmedAmount,
                                type = when (typeStr) {
                                    "INCOME" -> TransactionType.INCOME
                                    "TRANSFER" -> TransactionType.TRANSFER
                                    else -> TransactionType.EXPENSE
                                },
                                accountId = accountId,
                                categoryId = categoryId ?: 0,
                                merchant = result.merchant,
                                note = note
                            )
                        }
                    },
                    onCancel = {
                        Log.d(TAG, "${platform.label}: user cancelled")
                    }
                )
            } else {
                // 无悬浮权限 — 直接自动记账（切回 IO 执行）
                val type = when (result.rule.type) {
                    "INCOME" -> TransactionType.INCOME
                    "TRANSFER" -> TransactionType.TRANSFER
                    else -> TransactionType.EXPENSE
                }
                scope.launch {
                    saveTransaction(
                        platform = platform,
                        amount = amount,
                        type = type,
                        accountId = 1,
                        categoryId = 0,
                        merchant = result.merchant,
                        note = result.merchant ?: platform.label
                    )
                }
            }
        }
    }

    private suspend fun saveTransaction(
        platform: Platform,
        amount: Long,
        type: TransactionType,
        accountId: Long,
        categoryId: Long,
        merchant: String?,
        note: String?
    ) {
        try {
            val finalCategoryId = if (categoryId != 0L) categoryId else {
                val cats = categoryRepository.getAllCategories().first()
                val catNames = cats.map { it.name }
                val classification = localModel.classifyTransaction(
                    merchant = merchant,
                    note = null,
                    amount = amount,
                    existingCategories = catNames
                )
                if (classification.confidence >= 0.4f && classification.explanation != null) {
                    cats.find { it.name == classification.explanation }?.id ?: 0
                } else 0
            }

            val finalNote = note ?: merchant ?: "[${platform.label}]"

            transactionRepository.insert(
                Transaction(
                    amount = amount,
                    type = type,
                    categoryId = finalCategoryId,
                    accountId = accountId,
                    note = finalNote,
                    date = System.currentTimeMillis()
                )
            )
            showNotification(
                "已记账 [${platform.label}]",
                "$finalNote ${"%.2f元".format(amount / 100.0)}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Save failed", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  内容指纹去重
    // ═══════════════════════════════════════════════════════════════

    private fun generateFingerprint(pkg: String, amount: Long, type: String?, text: String): String {
        return "$pkg|$amount|${type ?: ""}|${text.hashCode()}"
    }

    private fun isDuplicate(pkg: String, amount: Long, type: String?, text: String): Boolean {
        val fp = generateFingerprint(pkg, amount, type, text)
        val now = System.currentTimeMillis()
        if (pkg != lastFingerprintPackage) {
            lastFingerprint = null
            lastFingerprintPackage = pkg
        }
        if (fp == lastFingerprint && now - lastFingerprintTime < FINGERPRINT_COOLDOWN) {
            return true
        }
        lastFingerprint = fp
        lastFingerprintTime = now
        return false
    }

    // ═══════════════════════════════════════════════════════════════
    //  保活
    // ═══════════════════════════════════════════════════════════════

    private fun createKeepAliveOverlay() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 0
            val view = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
            wm.addView(view, params)
            keepAliveView = view
            Log.d(TAG, "Keep-alive overlay created")
        } catch (e: Exception) {
            Log.w(TAG, "Keep-alive overlay failed", e)
        }
    }

    private fun removeKeepAliveOverlay() {
        keepAliveView?.let { view ->
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (_: Exception) {}
            keepAliveView = null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  通知
    // ═══════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "payment_detect", "支付检测",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "检测到支付时自动记录并发送通知"
            enableVibration(true)
            setShowBadge(true)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, content: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, "payment_detect")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    // ── 剪贴板监听器 ──

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip ?: return@OnPrimaryClipChangedListener
            if (clip.itemCount == 0) return@OnPrimaryClipChangedListener
            val text = clip.getItemAt(0).text?.toString() ?: return@OnPrimaryClipChangedListener
            if (text == clipboardText) return@OnPrimaryClipChangedListener
            clipboardText = text

            Log.d(TAG, "Clipboard changed: ${text.take(100)}")

            // 解析剪贴板中的支付信息 — LLM 全程参与
            scope.launch {
                try {
                    val parsed = localModel.parsePaymentText(text)
                    if (parsed.amount != null && parsed.amount > 0) {
                        Log.i(TAG, "Clipboard LLM parsed: ${parsed.amount / 100.0}元 conf=${parsed.confidence}")
                        CaptureLogManager.log(
                            platform = "剪贴板", amount = parsed.amount,
                            merchant = parsed.merchant, layer = 0, source = "clipboard-llm",
                            confidence = parsed.confidence
                        )

                        // LLM 自动分类
                        val cats = categoryRepository.getAllCategories().first()
                        val catNames = cats.map { it.name }
                        val classification = localModel.classifyTransaction(
                            merchant = parsed.merchant,
                            note = parsed.note,
                            amount = parsed.amount,
                            existingCategories = catNames
                        )
                        val categoryId = if (classification.confidence >= 0.4f && classification.explanation != null) {
                            cats.find { it.name == classification.explanation }?.id ?: 0
                        } else 0

                        transactionRepository.insert(
                            Transaction(
                                amount = parsed.amount,
                                type = TransactionType.EXPENSE,
                                categoryId = categoryId,
                                accountId = 1,
                                note = parsed.note ?: parsed.merchant ?: "剪贴板",
                                date = System.currentTimeMillis()
                            )
                        )
                        showNotification(
                            "已自动记账 [剪贴板]",
                            "${parsed.merchant ?: ""} ${"%.2f元".format(parsed.amount / 100.0)}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Clipboard LLM processing failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard listener error", e)
        }
    }

    companion object {
        private const val TAG = "PaymentAccessibility"
    }
}
