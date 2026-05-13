package com.anaya.app.util

/**
 * 自动记账检测日志 — 全局单例
 *
 * 记录每次支付检测的详细链路信息，供 SmartCapture 和设置页的日志面板展示。
 */
object CaptureLogManager {

    data class LogEntry(
        val id: Long = System.nanoTime(),
        val timestamp: Long = System.currentTimeMillis(),
        val platform: String = "",
        val amount: Long = 0,
        val merchant: String? = null,
        /** L1=节点树, L2=事件文本, L3=截图OCR */
        val layer: Int = 0,
        /** clipboard, accessibility, ocr */
        val source: String = "",
        val status: Status = Status.PENDING,
        val confidence: Float = 0f,
        val note: String? = null
    )

    enum class Status(val label: String) {
        PENDING("待处理"),
        SAVED("已记账"),
        DISMISSED("已忽略"),
        FAILED("失败")
    }

    private val _logs = mutableListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs.toList()
    val recentLogs: List<LogEntry> get() = _logs.takeLast(50).reversed()

    private var lastDedupeKey: String? = null
    private var lastDedupeTime = 0L
    private val DEDUPE_COOLDOWN = 10_000L // 10秒内相同平台+金额的去重

    /**
     * 记录一次检测事件（带去重）
     * @return 是否是新记录（true=记录成功, false=重复已忽略）
     */
    fun log(
        platform: String, amount: Long, merchant: String?,
        layer: Int, source: String, confidence: Float,
        note: String? = null
    ): Boolean {
        val dedupeKey = "$platform|$amount|$merchant"
        val now = System.currentTimeMillis()
        if (dedupeKey == lastDedupeKey && now - lastDedupeTime < DEDUPE_COOLDOWN) {
            return false
        }
        lastDedupeKey = dedupeKey
        lastDedupeTime = now

        val entry = LogEntry(
            platform = platform, amount = amount, merchant = merchant,
            layer = layer, source = source, status = Status.PENDING,
            confidence = confidence, note = note
        )
        _logs.add(entry)
        if (_logs.size > 200) {
            _logs.removeAt(0)
        }
        return true
    }

    fun updateStatus(id: Long, status: Status) {
        val idx = _logs.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _logs[idx] = _logs[idx].copy(status = status)
        }
    }

    fun clear() {
        _logs.clear()
        lastDedupeKey = null
        lastDedupeTime = 0L
    }

    fun getLayerLabel(layer: Int): String = when (layer) {
        1 -> "节点规则"
        2 -> "事件文本"
        3 -> "截图OCR"
        else -> "未知"
    }

    fun getLayerColor(layer: Int): Long = when (layer) {
        1 -> 0xFF4A90D9L   // 蓝
        2 -> 0xFF34C759L   // 绿
        3 -> 0xFFFF9500L   // 橙
        else -> 0xFF8E8E93L // 灰
    }
}
