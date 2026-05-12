package com.anaya.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.ParsedTransaction
import com.anaya.app.ml.RuleBasedParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * 智能支付识别服务 — 参考"自动记账"的识别架构
 *
 * 工作流程：
 * 1. 监听窗口变化事件
 * 2. 根据 event.packageName 判断支付平台
 * 3. 尝试 rootInActiveWindow 获取完整页面文本
 * 4. 若 root 为 null（国产手机常见限制），改用 event.text 降级识别
 * 5. 识别到支付完成后提取金额/商户，自动记账
 */
@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {

    @Inject lateinit var localModel: LocalModelInterface
    @Inject lateinit var eventBus: PaymentEventBus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recognizer = PlatformPageRecognizer()

    /** 上次识别到的页面类型 */
    private var lastPageType: PageType = PageType.UNKNOWN

    /** 上次识别的时间戳（防重复触发） */
    private val lastCaptureTime = AtomicLong(0L)

    /** 上次识别的平台（防重复触发） */
    private var lastPlatformPkg: String = ""

    /** 等待用户点击展开的页面类型队列 */
    private var pendingPageType: PageType? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 不设置 packageNames 过滤器：国产 ROM 上过滤可能导致事件丢失
        // 所有 App 的 TYPE_WINDOW_STATE_CHANGED 都会送达，我们在代码里过滤
        serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 500
        }
        Log.i(TAG, "Service connected, monitoring all window changes")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 只处理窗口切换事件（每个新页面触发一次，性能开销最小）
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        val platform = Platform.fromPackage(pkg)

        // 非支付平台 → 跳过
        if (platform == null) return

        // 同平台防冲：200ms 内同一平台不重复处理
        val now = System.currentTimeMillis()
        if (pkg == lastPlatformPkg && now - lastCaptureTime.get() < 200) return
        lastPlatformPkg = pkg

        Log.d(TAG, "Window change: ${platform.label}, event text: ${event.text?.joinToString(" | ") ?: "(空)"}")

        // 尝试获取窗口内容（国产手机上可能返回 null）
        val root = rootInActiveWindow
        val result: PageRecognition

        if (root != null) {
            try {
                result = recognizer.recognize(root, platform)
            } catch (e: Exception) {
                Log.e(TAG, "Recognize from root failed", e)
                return
            } finally {
                recycleNode(root)
            }
        } else {
            // rootInActiveWindow = null（MIUI/HarmonyOS/ColorOS 常见）
            // 使用 event.text 做降级识别
            Log.d(TAG, "rootInActiveWindow is null, using event text fallback")
            val eventText = event.text.joinToString("")
            val eventDesc = event.contentDescription?.toString() ?: ""
            result = recognizer.lightRecognize(platform, eventText, eventDesc)
        }

        Log.d(TAG, "Recognition: ${platform.label} → ${result.pageType.label} (conf=${result.confidence})")

        // 跳过未知页面
        if (result.pageType == PageType.UNKNOWN) {
            lastPageType = PageType.UNKNOWN
            return
        }

        when (result.pageType) {
            PageType.ALL_ORDER_INFO,
            PageType.PAYMENT_METHOD -> {
                pendingPageType = result.pageType
                lastPageType = result.pageType
            }

            PageType.PAYMENT_COMPLETE,
            PageType.RED_PACKET,
            PageType.TRANSFER,
            PageType.ORDER_DETAIL -> {
                // 有 pending 标记时走延迟捕获
                if (result.pageType == PageType.ORDER_DETAIL && pendingPageType != null) {
                    launchDelayedCapture(platform)
                    pendingPageType = null
                } else {
                    capturePayment(result)
                }
            }

            PageType.WALLET_BILL -> capturePayment(result)

            PageType.UNKNOWN -> { /* ignore */ }
        }

        lastPageType = result.pageType
    }

    /**
     * 捕获支付数据
     */
    private fun capturePayment(result: PageRecognition) {
        // 防重复：2秒内同平台不重复触发
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime.get() < 2000) return
        lastCaptureTime.set(now)

        val pageText = result.pageText
        if (pageText.isBlank()) {
            Log.w(TAG, "Page text is blank, cannot extract payment info")
            return
        }

        scope.launch {
            try {
                // 1. 先用规则解析器快速提取
                val parsed = RuleBasedParser.parsePaymentText(pageText)
                Log.d(TAG, "Rule parse: amount=${parsed.amount}, merchant=${parsed.merchant}, conf=${parsed.confidence}")

                // 2. 如果有本地模型，用模型做二次精确解析
                val finalParsed = if (localModel.isModelLoaded() && parsed.confidence < 0.8f) {
                    val mlResult = localModel.parsePaymentText(pageText)
                    if (mlResult.confidence > parsed.confidence) mlResult else parsed
                } else parsed

                if (finalParsed.amount != null || finalParsed.merchant != null) {
                    val resultWithPlatform = finalParsed.copy(
                        note = finalParsed.note
                            ?: "[${result.platform?.label}][${result.pageType.label}]"
                    )
                    eventBus.emit(resultWithPlatform)

                    val amountStr = finalParsed.amount?.let {
                        "%.2f元".format(it / 100.0)
                    } ?: ""
                    val merchantStr = finalParsed.merchant ?: ""
                    Log.i(TAG, "Auto-saved: ${result.platform?.label} $merchantStr $amountStr")
                    showNotification(
                        "已自动记账 [${result.platform?.label}]",
                        "$merchantStr $amountStr"
                    )
                } else {
                    Log.w(TAG, "No amount/merchant extracted from: ${pageText.take(100)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "capturePayment failed", e)
            }
        }
    }

    /**
     * 延迟捕获 — 用于用户展开详情后的页面
     * 等页面加载完（1.5s）再抓取
     */
    private fun launchDelayedCapture(platform: Platform) {
        scope.launch {
            delay(1500)
            val newRoot = rootInActiveWindow
            if (newRoot != null) {
                try {
                    val result = recognizer.recognize(newRoot, platform)
                    if (result.pageType == PageType.ORDER_DETAIL ||
                        result.pageType == PageType.PAYMENT_COMPLETE) {
                        capturePayment(result)
                    }
                } finally {
                    recycleNode(newRoot)
                }
            }
        }
    }

    private fun recycleNode(node: AccessibilityNodeInfo?) {
        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                @Suppress("DEPRECATION")
                node?.recycle()
            }
        } catch (_: Exception) {}
    }

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

    companion object {
        private const val TAG = "PaymentAccessibility"
    }
}
