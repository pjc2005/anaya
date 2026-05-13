package com.anaya.app.presentation.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.ml.LocalModelManager
import com.anaya.app.ml.RuleBasedClassifier
import com.anaya.app.ml.RuleBasedParser
import com.anaya.app.service.AccessibilityDebugState
import com.anaya.app.util.CaptureLogManager
import com.anaya.app.util.CurrencyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TestParseResult(
    val amount: String = "未识别",
    val merchant: String = "未识别",
    val category: String = "未识别",
    val confidence: Int = 0,
    val source: String = "",
    val rawResponse: String = ""
)

data class AutoCaptureLogUiState(
    val testInput: String = "",
    val llmResult: TestParseResult? = null,
    val ruleResult: TestParseResult? = null,
    val isParsingLlm: Boolean = false,
    val isParsingRule: Boolean = false,
    val debugInfo: String = "",
    val debugTimestamp: String = ""
)

@HiltViewModel
class AutoCaptureLogViewModel @Inject constructor(
    private val localModel: LocalModelManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "AutoCaptureLogVM"
    }

    private val _state = MutableStateFlow(AutoCaptureLogUiState())
    val state: StateFlow<AutoCaptureLogUiState> = _state.asStateFlow()

    fun onTestInputChanged(text: String) {
        _state.value = _state.value.copy(testInput = text)
    }

    /** 仅规则解析 */
    fun parseWithRule() {
        val text = _state.value.testInput.trim()
        if (text.isBlank()) return
        _state.value = _state.value.copy(isParsingRule = true, ruleResult = null)

        val parsed = RuleBasedParser.parsePaymentText(text)
        val catName = RuleBasedClassifier.suggestCategoryName(parsed.merchant, parsed.note)

        _state.value = _state.value.copy(
            isParsingRule = false,
            ruleResult = TestParseResult(
                amount = parsed.amount?.let { CurrencyUtils.centsToDisplayString(it) } ?: "未识别",
                merchant = parsed.merchant ?: "未识别",
                category = catName ?: "未识别",
                confidence = (parsed.confidence * 100).toInt(),
                source = "规则引擎",
                rawResponse = ""
            )
        )
    }

    /** LLM 解析 */
    fun parseWithLlm() {
        val text = _state.value.testInput.trim()
        if (text.isBlank()) return
        _state.value = _state.value.copy(isParsingLlm = true, llmResult = null)

        viewModelScope.launch {
            try {
                val parsed = localModel.parsePaymentText(text)
                val catNames = listOf("餐饮", "交通", "购物", "娱乐", "医疗", "教育", "住房", "通讯", "工资", "红包", "其他")
                val classification = localModel.classifyTransaction(
                    merchant = parsed.merchant,
                    note = parsed.note,
                    amount = parsed.amount,
                    existingCategories = catNames
                )

                _state.value = _state.value.copy(
                    isParsingLlm = false,
                    llmResult = TestParseResult(
                        amount = parsed.amount?.let { CurrencyUtils.centsToDisplayString(it) } ?: "未识别",
                        merchant = parsed.merchant ?: "未识别",
                        category = classification.explanation ?: "未识别",
                        confidence = (parsed.confidence * 100).toInt(),
                        source = "本地 0.5B LLM",
                        rawResponse = "conf=${parsed.confidence}"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "LLM parse failed", e)
                _state.value = _state.value.copy(
                    isParsingLlm = false,
                    llmResult = TestParseResult(
                        source = "❌ LLM 调用失败: ${e.message?.take(80) ?: "未知错误"}"
                    )
                )
            }
        }
    }

    /** 刷新调试信息 */
    fun refreshDebugInfo() {
        val dump = AccessibilityDebugState.dump()
        val ts = if (AccessibilityDebugState.lastScanTime > 0) {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(java.util.Date(AccessibilityDebugState.lastScanTime))
        } else ""
        _state.value = _state.value.copy(debugInfo = dump, debugTimestamp = ts)
    }

    /** 复制节点文本到测试输入框 */
    fun copyRootTextToTest() {
        if (AccessibilityDebugState.lastRootNodeText.isNotBlank()) {
            _state.value = _state.value.copy(testInput = AccessibilityDebugState.lastRootNodeText)
        }
    }

    /** 复制事件文本到测试输入框 */
    fun copyEventTextToTest() {
        if (AccessibilityDebugState.lastEventText.isNotBlank()) {
            _state.value = _state.value.copy(testInput = AccessibilityDebugState.lastEventText)
        }
    }

    /** 导出诊断信息到文件（可分享给我分析） */
    fun exportDiagnosticData() {
        viewModelScope.launch {
            try {
                val logs = CaptureLogManager.recentLogs
                val dump = buildString {
                    appendLine("═══════════════════════════════════════════")
                    appendLine("  Anaya 自动记账诊断报告")
                    appendLine("  导出时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}")
                    appendLine("═══════════════════════════════════════════")
                    appendLine()
                    appendLine("【无障碍服务调试状态】")
                    appendLine(AccessibilityDebugState.dump())
                    appendLine()
                    appendLine("【最近 30 条检测日志】")
                    if (logs.isEmpty()) {
                        appendLine("  (无记录)")
                    } else {
                        logs.takeLast(30).forEachIndexed { i, entry ->
                            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                                .format(java.util.Date(entry.timestamp))
                            appendLine("  #${i + 1} [$ts] ${entry.platform} L${entry.layer}")
                            appendLine("      金额=${CurrencyUtils.centsToDisplayString(entry.amount)} 商户=${entry.merchant ?: "?"}")
                            appendLine("      置信度=${(entry.confidence * 100).toInt()}% 来源=${entry.source} 状态=${entry.status.label}")
                        }
                    }
                    appendLine()
                    appendLine("【用户输入测试】")
                    appendLine("  输入: ${_state.value.testInput.take(200)}")
                    appendLine("  LLM结果: ${_state.value.llmResult?.let { "${it.source}: ¥${it.amount} → ${it.merchant} (${it.category})" } ?: "未测试"}")
                    appendLine("  规则结果: ${_state.value.ruleResult?.let { "${it.source}: ¥${it.amount} → ${it.merchant} (${it.category})" } ?: "未测试"}")
                    appendLine()
                    appendLine("── 诊断结束 ──")
                }

                val file = java.io.File(appContext.getExternalFilesDir(null), "anaya_diagnostic.txt")
                file.parentFile?.mkdirs()
                file.writeText(dump)

                _state.value = _state.value.copy(
                    debugInfo = dump,
                    debugTimestamp = "✅ 已导出到: ${file.absolutePath}"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    debugTimestamp = "❌ 导出失败: ${e.message?.take(80)}"
                )
            }
        }
    }
}
