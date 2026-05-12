package com.anaya.app.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 平台枚举 — 覆盖自动记账支持的所有支付平台
 */
enum class Platform(val packageName: String, val label: String) {
    WECHAT("com.tencent.mm", "微信"),
    ALIPAY("com.eg.android.AlipayGphone", "支付宝"),
    UNIONPAY("com.unionpay", "云闪付"),
    JD("com.jingdong.app.mall", "京东"),
    MEITUAN("com.sankuai.meituan", "美团"),
    DOUYIN("com.ss.android.ugc.aweme", "抖音"),
    PDD("com.xunmeng.pinduoduo", "拼多多");

    companion object {
        fun fromPackage(pkg: String): Platform? =
            entries.find { it.packageName == pkg }

        /** 设置向导用的识别说明列表 */
        val guideEntries: List<Pair<String, String>> = listOf(
            "微信"     to "发红包后需点开红包才能识别",
            "支付宝"   to "支付完成后自动识别",
            "云闪付"   to "支付完成、订单详情页、支付消息详情",
            "京东"     to "支付完成、订单详情页（新版需点开'全部订单信息'）、钱包-账单",
            "美团"     to "支付完成、订单详情页、钱包-账单、外卖订单（需点开'支付方式'）",
            "抖音"     to "支付完成后自动识别",
            "拼多多"   to "订单详情页（需点开'查看更多订单和优惠信息'）"
        )
    }
}

/**
 * 页面类型 — 描述当前处于支付流程的哪个场景
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
    val pageText: String,            // 页面全部可见文本，用于后续数据提取
    val clickableTexts: List<String> // 可交互按钮的文本
)

/**
 * 平台页面识别器 — 参考自动记账的识别方式
 *
 * 核心思路：
 * 1. 通过 AccessibilityNodeInfo 的 packageName 判断平台
 * 2. 收集页面上所有文本 + 可点击按钮文本
 * 3. 根据平台+文本特征判断页面类型
 */
class PlatformPageRecognizer {

    /**
     * 识别入口
     * @param root 当前窗口的 AccessibilityNodeInfo 根节点
     */
    fun recognize(root: AccessibilityNodeInfo): PageRecognition {
        val pkg = root.packageName?.toString() ?: ""
        val platform = Platform.fromPackage(pkg)

        if (platform == null) {
            return PageRecognition(
                platform = null,
                pageType = PageType.UNKNOWN,
                confidence = 0f,
                pageText = "",
                clickableTexts = emptyList()
            )
        }

        val visibleTexts = collectVisibleText(root)
        val clickableTexts = collectClickableTexts(root)
        val pageText = visibleTexts.joinToString("\n")

        val pageType = identifyPageType(platform, visibleTexts, clickableTexts)

        return PageRecognition(
            platform = platform,
            pageType = pageType,
            confidence = if (pageType == PageType.UNKNOWN) 0f else 0.85f,
            pageText = pageText,
            clickableTexts = clickableTexts
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
        // 可点击且子节点数为 0 的文本按钮
        if (node.isClickable && node.text != null) {
            result.add(node.text.toString())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { result.addAll(collectClickableTexts(it)) }
        }
        return result
    }

    // ── 按平台+文本特征匹配页面类型 ──

    private fun identifyPageType(
        platform: Platform,
        texts: List<String>,
        clickableTexts: List<String>
    ): PageType {
        val full = texts.joinToString("")
        val clicks = clickableTexts.joinToString("")

        return when (platform) {
            Platform.WECHAT -> when {
                "红包" in full && ("查看红包" in full || "开" in texts) -> PageType.RED_PACKET
                "转账给你" in full || "确认收款" in full -> PageType.TRANSFER
                "支付成功" in full || "交易成功" in full || "支付完成" in full -> PageType.PAYMENT_COMPLETE
                "账单" in full && "本月" in full -> PageType.WALLET_BILL
                else -> PageType.UNKNOWN
            }

            Platform.ALIPAY -> when {
                "支付成功" in full || "付款成功" in full || "交易成功" in full -> PageType.PAYMENT_COMPLETE
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
                "全部订单信息" in clicks -> {
                    // 用户刚点了"全部订单信息"→ 等下一帧重新识别
                    PageType.ALL_ORDER_INFO
                }
                "支付成功" in full || "支付完成" in full -> PageType.PAYMENT_COMPLETE
                "订单详情" in full -> PageType.ORDER_DETAIL
                "账单" in full || "钱包" in full -> PageType.WALLET_BILL
                else -> PageType.UNKNOWN
            }

            Platform.MEITUAN -> when {
                "支付方式" in clicks || "支付方式" in full -> {
                    PageType.PAYMENT_METHOD
                }
                "支付成功" in full || "支付完成" in full -> PageType.PAYMENT_COMPLETE
                "订单详情" in full -> PageType.ORDER_DETAIL
                "账单" in full || "钱包" in full -> PageType.WALLET_BILL
                else -> PageType.UNKNOWN
            }

            Platform.DOUYIN -> when {
                "支付成功" in full || "支付完成" in full -> PageType.PAYMENT_COMPLETE
                else -> PageType.UNKNOWN
            }

            Platform.PDD -> when {
                "查看更多订单和优惠信息" in clicks -> {
                    // 用户点了展开按钮，等下一帧重新识别
                    PageType.ALL_ORDER_INFO
                }
                "订单详情" in full -> PageType.ORDER_DETAIL
                "支付成功" in full || "支付完成" in full -> PageType.PAYMENT_COMPLETE
                else -> PageType.UNKNOWN
            }
        }
    }
}
