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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {

    @Inject lateinit var localModel: LocalModelInterface
    @Inject lateinit var eventBus: PaymentEventBus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val PAYMENT_KEYWORDS = listOf(
        "支付成功", "付款成功", "购买成功", "交易完成",
        "微信支付", "支付宝", "收到红包", "转账",
        "¥", "￥", "金额"
    )

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
            packageNames = arrayOf(
                "com.tencent.mm",
                "com.eg.android.AlipayGphone",
                "com.xunmeng.pinduoduo",
                "com.taobao.taobao",
                "com.jd.jd"
            )
        }
        Log.i("PaymentAccessibility", "Service connected, monitoring payment apps")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val root = rootInActiveWindow ?: return
        try {
            val text = extractAllText(root)
            if (text.any { PAYMENT_KEYWORDS.any { kw -> it.contains(kw) } }) {
                val fullText = text.joinToString(" ")
                scope.launch {
                    val parsed = localModel.parsePaymentText(fullText)
                    if (parsed.amount != null || parsed.merchant != null) {
                        eventBus.emit(parsed)
                        val amountStr = parsed.amount?.let { "%.0f元".format(it / 100.0) } ?: ""
                        val merchantStr = parsed.merchant ?: ""
                        showNotification("检测到支付", "$merchantStr $amountStr")
                    }
                }
            }
        } finally {
            recycleNode(root)
        }
    }

    private fun extractAllText(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                texts.addAll(extractAllText(child))
                recycleNode(child)
            }
        }
        return texts
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
        )
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
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onInterrupt() {
        Log.d("PaymentAccessibility", "Service interrupted")
    }
}
