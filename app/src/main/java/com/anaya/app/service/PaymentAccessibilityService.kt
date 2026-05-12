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
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * 支付检测服务 — 简化版
 *
 * 核心逻辑：收到支付 App 的窗口变化事件 → 收集页面所有可见文本
 * → 正则提取金额 → 有金额就自动记账。不依赖页面类型识别。
 */
@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {

    @Inject lateinit var localModel: LocalModelInterface
    @Inject lateinit var eventBus: PaymentEventBus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 上次触发时间（防重复） */
    private val lastCaptureTime = AtomicLong(0L)

    /** 前台通知 ID */
    private val FOREGROUND_NOTIFY_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 前台服务（防国产 ROM 杀后台）
        val fgNotification = NotificationCompat.Builder(this, "payment_detect")
            .setContentTitle("Anaya 支付检测运行中")
            .setContentText("自动识别微信、支付宝等支付信息")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(FOREGROUND_NOTIFY_ID, fgNotification)

        serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 500
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
        Log.i(TAG, "Service connected (foreground)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        val platform = Platform.fromPackage(pkg) ?: return

        // 防重复：3 秒内同 App 不重复处理
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime.get() < 3000) return
        lastCaptureTime.set(now)

        Log.d(TAG, "Window change: ${platform.label}")

        // 收集页面文本
        val text = collectPageText(event)
        if (text.isBlank()) {
            Log.d(TAG, "${platform.label}: page text is empty")
            return
        }

        Log.d(TAG, "${platform.label}: text=${text.take(200)}")

        // 直接解析支付信息（不判断页面类型）
        scope.launch {
            try {
                val parsed = RuleBasedParser.parsePaymentText(text)
                if (parsed.amount != null && parsed.amount > 0) {
                    Log.i(TAG, "Payment detected: ${platform.label} ${parsed.merchant ?: ""} ${parsed.amount / 100.0}元")

                    val enriched = parsed.copy(
                        note = parsed.note ?: "[${platform.label}]",
                        confidence = parsed.confidence.coerceAtLeast(0.6f)
                    )
                    eventBus.emit(enriched)

                    showNotification(
                        "已自动记账 [${platform.label}]",
                        "${parsed.merchant ?: ""} ${"%.2f元".format(parsed.amount / 100.0)}"
                    )
                } else {
                    Log.d(TAG, "${platform.label}: no amount found in page text")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Parse failed", e)
            }
        }
    }

    /**
     * 收集页面上所有可见文本
     * 优先使用 rootInActiveWindow，不可用时回退到 event.text
     */
    private fun collectPageText(event: AccessibilityEvent): String {
        val root = rootInActiveWindow
        if (root != null) {
            try {
                val texts = mutableListOf<String>()
                collectText(root, texts)
                if (texts.isNotEmpty()) return texts.joinToString("\n")
            } catch (e: Exception) {
                Log.w(TAG, "collectText from root failed", e)
            } finally {
                recycleNode(root)
            }
        }
        // root 不可用时用 event.text
        val eventText = event.text?.joinToString(" ") ?: ""
        val eventDesc = event.contentDescription?.toString() ?: ""
        return "$eventText $eventDesc".trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, result: MutableList<String>) {
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        if (text != null) result.add(text)
        if (desc != null) result.add(desc)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, result) }
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
