package com.anaya.app.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 平台枚举
 */
enum class Platform(val packageName: String, val label: String) {
    WECHAT("com.tencent.mm", "微信"),
    ALIPAY("com.eg.android.AlipayGphone", "支付宝"),
    UNIONPAY("com.unionpay", "云闪付"),
    JD("com.jingdong.app.mall", "京东"),
    MEITUAN("com.sankuai.meituan", "美团"),
    DOUYIN("com.ss.android.ugc.aweme", "抖音"),
    PDD("com.xunmeng.pinduoduo", "拼多多"),
    TAOBAO("com.taobao.taobao", "淘宝"),
    ELEME("me.ele", "饿了么");

    companion object {
        fun fromPackage(pkg: String): Platform? =
            entries.find { it.packageName == pkg }

        val guideEntries: List<Pair<String, String>> = listOf(
            "微信"     to "发红包后需点开红包才能识别",
            "支付宝"   to "支付完成后自动识别",
            "云闪付"   to "支付完成、订单详情页、支付消息详情",
            "京东"     to "支付完成、订单详情页（新版需点开'全部订单信息'）、钱包-账单",
            "美团"     to "支付完成、订单详情页、钱包-账单、外卖订单（需点开'支付方式'）",
            "抖音"     to "支付完成后自动识别",
            "拼多多"   to "订单详情页（需点开'查看更多订单和优惠信息'）",
            "淘宝"     to "支付完成页自动识别",
            "饿了么"   to "支付完成、订单详情自动识别"
        )
    }
}

/**
 * 页面类型
 */
enum class PageType(val label: String) {
    PAYMENT_COMPLETE("支付完成页"),
    ORDER_DETAIL("订单详情页"),
    WALLET_BILL("钱包-账单"),
    RED_PACKET("红包详情"),
    PAYMENT_METHOD("支付方式页"),
    ALL_ORDER_INFO("全部订单信息页"),
    TRANSFER("转账详情页"),
    UNKNOWN("未知页面")
}

/**
 * 识别结果
 */
data class PageRecognition(
    val platform: Platform?,
    val pageType: PageType,
    val confidence: Float,
    val pageText: String,
    val clickableTexts: List<String>
)

/**
 * 平台页面识别器
 *
 * 双模式识别：
 * 1. 完整模式 (recognize) — 需要 AccessibilityNodeInfo root，扫描全部文本
 * 2. 轻量模式 (lightRecognize) — 使用 event.text，国产 ROM 降级方案
 */
class PlatformPageRecognizer {

    /**
     * 完整识别 — 从 AccessibilityNodeInfo 扫描页面全部文本
     */
    fun recognize(root: AccessibilityNodeInfo, platform: Platform): PageRecognition {
        val visibleTexts = collectVisibleText(root)
        val clickableTexts = collectClickableTexts(root)
        val pageText = visibleTexts.joinToString("\n")

        val pageType = identifyPageType(platform, visibleTexts, clickableTexts, pageText)

        return PageRecognition(
            platform = platform,
            pageType = pageType,
            confidence = if (pageType == PageType.UNKNOWN) 0f else 0.85f,
            pageText = pageText,
            clickableTexts = clickableTexts
        )
    }

    /**
     * 轻量识别 — 使用 event.text 降级识别
     * 适用于 rootInActiveWindow 为 null 的国产手机
     */
    fun lightRecognize(
        platform: Platform,
        eventText: String,
        eventDesc: String
    ): PageRecognition {
        val combined = "$eventText $eventDesc".trim()

        val pageType = detectPageFromFragment(platform, combined)

        return PageRecognition(
            platform = platform,
            pageType = pageType,
            confidence = if (pageType == PageType.UNKNOWN) 0f else 0.7f,
            pageText = combined,
            clickableTexts = emptyList()
        )
    }

    // ── 节点树遍历 ──

    private fun collectVisibleText(node: AccessibilityNodeInfo): MutableList<String> {
        val result = mutableListOf<String>()
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        if (text != null) result.add(text)
        if (desc != null) result.add(desc)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { result.addAll(collectVisibleText(it)) }
        }
        return result
    }

    private fun collectClickableTexts(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        if (node.isClickable && node.text != null) {
            result.add(node.text.toString())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { result.addAll(collectClickableTexts(it)) }
        }
        return result
    }

    // ── 完整页面识别 ──

    private fun identifyPageType(
        platform: Platform,
        texts: List<String>,
        clickableTexts: List<String>,
        pageText: String
    ): PageType {
        val full = pageText
        val clicks = clickableTexts.joinToString("")

        val isPaymentComplete = isPaymentCompletePage(full, clicks)

        return when (platform) {
            Platform.WECHAT -> when {
                "红包" in full && ("查看红包" in full || "开" in texts) -> PageType.RED_PACKET
                "转账给你" in full || "确认收款" in full -> PageType.TRANSFER
                isPaymentComplete -> PageType.PAYMENT_COMPLETE
                "账单" in full && "本月" in full -> PageType.WALLET_BILL
                else -> PageType.UNKNOWN
            }

            Platform.ALIPAY -> when {
                isPaymentComplete -> PageType.PAYMENT_COMPLETE
                "账单" in full && ("收支" in full || "月" in full) -> PageType.WALLET_BILL
                "转账" in full && "确认" in full -> PageType.TRANSFER
                else -> PageType.UNKNOWN
            }

            Platform.UNIONPAY -> when {
                "支付成功" in full || "交易完成" in full -> PageType.PAYMENT_COMPLETE
                "订单详情" in full -> PageType.ORDER_DETAIL
                "支付消息" in full -> PageType.PAYMENT_COMPLETE
                else -> PageType.UNKNOWN
            }

            Platform.JD -> when {
                "全部订单信息" in clicks -> PageType.ALL_ORDER_INFO
                isPaymentComplete -> PageType.PAYMENT_COMPLETE
                "订单详情" in full -> PageType.ORDER_DETAIL
                "账单" in full || "钱包" in full -> PageType.WALLET_BILL
                else -> PageType.UNKNOWN
            }

            Platform.MEITUAN -> when {
                "支付方式" in clicks || "支付方式" in full -> PageType.PAYMENT_METHOD
                isPaymentComplete -> PageType.PAYMENT_COMPLETE
                "订单详情" in full -> PageType.ORDER_DETAIL
                "账单" in full || "钱包" in full -> PageType.WALLET_BILL
                else -> PageType.UNKNOWN
            }

            Platform.DOUYIN -> when {
                isPaymentComplete -> PageType.PAYMENT_COMPLETE
                else -> PageType.UNKNOWN
            }

            Platform.PDD -> when {
                "查看更多订单和优惠信息" in clicks -> PageType.ALL_ORDER_INFO
                "订单详情" in full -> PageType.ORDER_DETAIL
                isPaymentComplete -> PageType.PAYMENT_COMPLETE
                else -> PageType.UNKNOWN
            }

            Platform.TAOBAO -> when {
                isPaymentComplete -> PageType.PAYMENT_COMPLETE
                "订单详情" in full -> PageType.ORDER_DETAIL
                "实付款" in full -> PageType.PAYMENT_COMPLETE
                "已完成" in full && "金额" in full -> PageType.PAYMENT_COMPLETE
                "账单" in full || "淘宝人生" in full -> PageType.WALLET_BILL
                else -> PageType.UNKNOWN
            }

            Platform.ELEME -> when {
                isPaymentComplete -> PageType.PAYMENT_COMPLETE
                "订单已提交" in full || "正在为你配送" in full -> PageType.PAYMENT_COMPLETE
                "订单详情" in full -> PageType.ORDER_DETAIL
                else -> PageType.UNKNOWN
            }
        }
    }

    /**
     * 从 event 片段文本识别页面类型
     * TYPE_WINDOW_STATE_CHANGED 的 event.text 通常是 Activity 标题
     */
    private fun detectPageFromFragment(
        platform: Platform,
        text: String
    ): PageType {
        val isPayment = listOf("支付成功", "付款成功", "交易成功", "支付完成",
            "交易已完成", "您已成功付款", "支付结果", "已完成", "成功")
            .any { text.contains(it) }

        if (isPayment) return PageType.PAYMENT_COMPLETE

        return when (platform) {
            Platform.WECHAT -> when {
                "红包" in text -> PageType.RED_PACKET
                "转账" in text -> PageType.TRANSFER
                "账单" in text -> PageType.WALLET_BILL
                "订单" in text || "详情" in text -> PageType.ORDER_DETAIL
                text.contains(Regex("[¥￥]?\\d+(\\.\\d{1,2})?")) -> PageType.PAYMENT_COMPLETE
                else -> PageType.UNKNOWN
            }
            Platform.ALIPAY -> when {
                "账单" in text -> PageType.WALLET_BILL
                "转账" in text -> PageType.TRANSFER
                text.contains(Regex("[¥￥]?\\d+(\\.\\d{1,2})?")) -> PageType.PAYMENT_COMPLETE
                "订单" in text || "详情" in text -> PageType.ORDER_DETAIL
                else -> PageType.UNKNOWN
            }
            else -> when {
                text.contains(Regex("[¥￥]?\\d+(\\.\\d{1,2})?")) -> PageType.PAYMENT_COMPLETE
                else -> PageType.UNKNOWN
            }
        }
    }

    /**
     * 判断是否为支付完成页（通用检测）
     */
    private fun isPaymentCompletePage(full: String, clicks: String): Boolean {
        val hasAmount = Regex("[¥￥]?\\s*\\d+(\\.\\d{1,2})?\\s*元?").containsMatchIn(full)
        val hasDoneButton = clicks.contains("完成")
        val paymentKeywords = listOf("支付成功", "付款成功", "交易成功", "支付完成",
            "交易已完成", "您已成功付款", "支付结果", "已完成")
        val hasPaymentKeyword = paymentKeywords.any { full.contains(it) }
        return hasPaymentKeyword || (hasAmount && hasDoneButton)
    }
}
