package com.anaya.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.ParsedTransaction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {

    @Inject lateinit var localModel: LocalModelInterface

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _paymentFlow = MutableSharedFlow<ParsedTransaction>(extraBufferCapacity = 5)
    val paymentFlow = _paymentFlow.asSharedFlow()

    private val PAYMENT_KEYWORDS = listOf(
        "支付成功", "付款成功", "购买成功", "交易完成",
        "微信支付", "支付宝", "收到红包", "转账",
        "¥", "￥", "金额"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 300
            packageNames = arrayOf(
                "com.tencent.mm",     // WeChat
                "com.eg.android.AlipayGphone",  // Alipay
                "com.xunmeng.pinduoduo",  // Pinduoduo
                "com.taobao.taobao",  // Taobao
                "com.jd.jd"           // JD
            )
        }
        Log.d("PaymentAccessibility", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        val text = extractAllText(root)
        root.recycle()

        if (text.any { PAYMENT_KEYWORDS.any { kw -> it.contains(kw) } }) {
            val fullText = text.joinToString(" ")
            scope.launch {
                val parsed = localModel.parsePaymentText(fullText)
                if (parsed.amount != null || parsed.merchant != null) {
                    _paymentFlow.tryEmit(parsed)
                }
            }
        }
    }

    private fun extractAllText(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) texts.add(text)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                texts.addAll(extractAllText(child))
                child.recycle()
            }
        }
        return texts
    }

    override fun onInterrupt() {
        Log.d("PaymentAccessibility", "Service interrupted")
    }
}
