package com.anaya.app.service

/**
 * 无障碍服务调试状态 — 记录服务最后一次扫描到的内容
 *
 * 用于「检测日志」页面查看无障碍服务实时看到了什么，
 * 无需触发支付即可诊断自动记账失败的原因。
 */
object AccessibilityDebugState {

    /** 最后一次扫描的平台 */
    var lastPlatform: String = ""
        private set

    /** 事件文本（从 event.text + event.contentDescription 拼接） */
    var lastEventText: String = ""
        private set

    /** Root 节点文本摘要（从 rootInActiveWindow 遍历得到的关键文本） */
    var lastRootNodeText: String = ""
        private set

    /** Layer1 规则匹配结果 */
    var lastRuleMatchResult: String = ""
        private set

    /** 扫描时间 */
    var lastScanTime: Long = 0L
        private set

    /** 是否成功捕获到支付信息 */
    var lastCaptureSuccess: Boolean = false
        private set

    /** 最后一次失败的层级和原因 */
    var lastFailureReason: String = ""
        private set

    fun recordScan(
        platform: String,
        eventText: String,
        rootNodeText: String,
        ruleMatchResult: String,
        success: Boolean,
        failureReason: String = ""
    ) {
        lastPlatform = platform
        lastEventText = eventText
        lastRootNodeText = rootNodeText
        lastRuleMatchResult = ruleMatchResult
        lastCaptureSuccess = success
        lastFailureReason = failureReason
        lastScanTime = System.currentTimeMillis()
    }

    fun clear() {
        lastPlatform = ""
        lastEventText = ""
        lastRootNodeText = ""
        lastRuleMatchResult = ""
        lastCaptureSuccess = false
        lastFailureReason = ""
        lastScanTime = 0L
    }

    /** 生成调试摘要 */
    fun dump(): String = buildString {
        appendLine("=== 无障碍服务调试信息 ===")
        if (lastScanTime == 0L) {
            appendLine("暂无捕获记录 — 打开微信/支付宝等支付 App 即可触发扫描")
            return@buildString
        }
        appendLine("平台: $lastPlatform")
        appendLine("扫描时间: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date(lastScanTime))}")
        appendLine("")
        appendLine("▶ Layer1 规则结果:")
        appendLine(if (lastRuleMatchResult.isNotBlank()) lastRuleMatchResult else "  未匹配到规则")
        appendLine("")
        appendLine("▶ Layer2 事件文本:")
        appendLine(if (lastEventText.isNotBlank()) "  ${lastEventText.take(500)}" else "  (空)")
        appendLine("")
        appendLine("▶ 节点树关键文本:")
        appendLine(if (lastRootNodeText.isNotBlank()) "  ${lastRootNodeText.take(500)}" else "  (rootInActiveWindow 为 null 或无可提取文本)")
        appendLine("")
        appendLine("▶ 捕获状态: ${if (lastCaptureSuccess) "✅ 成功" else "❌ 失败"}")
        if (lastFailureReason.isNotBlank()) {
            appendLine("▶ 失败原因: $lastFailureReason")
        }
    }
}
