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
 * 1. 监听支付 App 的窗口变化
 * 2. PlatformPageRecognizer 判断当前在哪个平台+页面类型
 * 3. 根据页面类型提取交易数据
 * 4. 对需要交互展开的页面（京东全部订单信息、美团支付方式等）
 *    延迟等待用户操作后再重新获取完整数据
 */
@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {

    @Inject lateinit var localModel: LocalModelInterface
    @Inject lateinit var eventBus: PaymentEventBus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recognizer = PlatformPageRecognizer()

    /** 上次识别到的页面类型（用于检测页面变化） */
    private var lastPageType: PageType = PageType.UNKNOWN

    /** 上次识别的时间戳（防重复触发） */
    private val lastCaptureTime = AtomicLong(0L)

    /** 等待用户点击展开的页面类型队列 */
    private var pendingPageType: PageType? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 300
            // 覆盖所有主流支付平台
            packageNames = arrayOf(
                "com.tencent.mm",                           // 微信
                "com.eg.android.AlipayGphone",                // 支付宝
                "com.unionpay",                               // 云闪付
                "com.jingdong.app.mall",                      // 京东
                "com.sankuai.meituan",                        // 美团
                "com.ss.android.ugc.aweme",                   // 抖音
                "com.xunmeng.pinduoduo"                       // 拼多多
            )
        }
        Log.i(TAG, "Service connected, monitoring all payment platforms")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val root = rootInActiveWindow ?: return

        try {
            val result = recognizer.recognize(root)

            // 跳过未知页面
            if (result.platform == null || result.pageType == PageType.UNKNOWN) {
                lastPageType = PageType.UNKNOWN
                return
            }

            Log.d(TAG, "Detected: ${result.platform.label} / ${result.pageType.label}")

            when (result.pageType) {
                // ── 需要用户点击展开后等待下一页 ──
                PageType.ALL_ORDER_INFO,
                PageType.PAYMENT_METHOD -> {
                    // 标记需要等待下一帧（用户点击后会跳转到详情页）
                    pendingPageType = result.pageType
                    lastPageType = result.pageType
                }

                // ── 直接捕获的交易页面 ──
                PageType.PAYMENT_COMPLETE -> {
                    captureTransaction(result)
                }

                // ── 用户点击展开后到达的详情页 ──
                PageType.ORDER_DETAIL -> {
                    // 如果前一步是"全部订单信息"或"支付方式"，说明展开后到了详情
                    if (pendingPageType != null) {
                        // 延迟等页面渲染完成再抓取
                        launchDelayedCapture(result)
                        pendingPageType = null
                    } else {
                        captureTransaction(result)
                    }
                }

                // ── 红包 / 转账 ──
                PageType.RED_PACKET -> {
                    captureTransaction(result)
                }
                PageType.TRANSFER -> {
                    captureTransaction(result)
                }

                // ── 钱包-账单页 ──
                PageType.WALLET_BILL -> {
                    // 账单页数据量大，只提取最近一条
                    captureTransaction(result)
                }

                PageType.UNKNOWN -> { /* 忽略 */ }
            }

            lastPageType = result.pageType

        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
        } finally {
            recycleNode(root)
        }
    }

    /**
     * 捕获交易数据
     */
    private fun captureTransaction(result: PageRecognition) {
        // 防重复：2秒内同平台同页面不重复触发
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime.get() < 2000) return
        lastCaptureTime.set(now)

        scope.launch {
            // 1. 先用规则解析器快速提取
            val parsed = RuleBasedParser.parsePaymentText(result.pageText)

            // 2. 如果有本地模型，用模型做二次精确解析
            val finalParsed = if (localModel.isModelLoaded() && parsed.confidence < 0.8f) {
                val mlResult = localModel.parsePaymentText(result.pageText)
                // 取置信度更高的结果
                if (mlResult.confidence > parsed.confidence) mlResult else parsed
            } else parsed

            if (finalParsed.amount != null || finalParsed.merchant != null) {
                val resultWithPlatform = finalParsed.copy(
                    note = finalParsed.note
                        ?: "[${result.platform?.label}][${result.pageType.label}]"
                )
                eventBus.emit(resultWithPlatform)

                val amountStr = resultWithPlatform.amount?.let {
                    "%.2f元".format(it / 100.0)
                } ?: ""
                val merchantStr = resultWithPlatform.merchant ?: ""
                Log.i(TAG, "Detected: ${result.platform?.label} $merchantStr $amountStr → auto-saved")
                showNotification(
                    "已自动记账 [${result.platform?.label}]",
                    "$merchantStr $amountStr"
                )
            } else {
                Log.w(TAG, "Detected ${result.platform?.label} but no amount/merchant extracted: ${result.pageText.take(80)}")
            }
        }
    }

    /**
     * 延迟捕获 — 用于用户展开详情后的页面
     * 等页面加载完（1.5s）再抓取完整信息
     */
    private fun launchDelayedCapture(result: PageRecognition) {
        scope.launch {
            delay(1500)
            // 重新获取当前页面信息
            val newRoot = rootInActiveWindow ?: return@launch
            try {
                val newResult = recognizer.recognize(newRoot)
                if (newResult.pageType == PageType.ORDER_DETAIL ||
                    newResult.pageType == PageType.PAYMENT_COMPLETE) {
                    captureTransaction(newResult)
                }
            } finally {
                recycleNode(newRoot)
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
