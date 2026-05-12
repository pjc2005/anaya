package com.anaya.app.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelStatus { NotDownloaded, Downloading, Ready, Error }

@Singleton
class LocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalModelInterface {

    private var isLoaded = false
    private var nativeAvailable = false

    private val _modelStatus = MutableStateFlow(ModelStatus.NotDownloaded)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    init {
        try {
            System.loadLibrary("llama")
            nativeAvailable = true
            Log.i("LocalModel", "Native llama.cpp library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.i("LocalModel", "Native library not available, using rule fallback: ${e.message}")
        }
    }

    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeInference(prompt: String, maxTokens: Int): String

    override fun isModelLoaded(): Boolean = isLoaded

    fun getModelDir(): String =
        context.filesDir.resolve("models").absolutePath

    fun getModelFilePath(): String =
        File(getModelDir(), LocalModelInterface.MODEL_FILENAME).absolutePath

    fun isModelFilePresent(): Boolean =
        File(getModelFilePath()).exists()

    fun getModelFileSize(): Long {
        val f = File(getModelFilePath())
        return if (f.exists()) f.length() else -1
    }

    fun checkModelStatus() {
        _modelStatus.value = if (isModelFilePresent()) ModelStatus.Ready else ModelStatus.NotDownloaded
    }

    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        if (isModelFilePresent()) {
            _modelStatus.value = ModelStatus.Ready
            return@withContext
        }
        _modelStatus.value = ModelStatus.Downloading
        _downloadProgress.value = 0
        try {
            val dir = File(getModelDir())
            dir.mkdirs()
            val url = URL("https://hf-mirror.com/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.connect()
            val totalBytes = conn.contentLengthLong
            val input = conn.inputStream
            val output = File(getModelFilePath()).outputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalBytes > 0) {
                    _downloadProgress.value = ((totalRead * 100) / totalBytes).toInt()
                }
            }
            output.close()
            input.close()
            conn.disconnect()
            _modelStatus.value = ModelStatus.Ready
            Log.i("LocalModel", "Model downloaded: ${getModelFilePath()}")
        } catch (e: Exception) {
            Log.e("LocalModel", "Download failed", e)
            _modelStatus.value = ModelStatus.Error
        }
    }

    override suspend fun loadModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(modelPath)
        if (!file.exists()) {
            val fallback = File(context.filesDir, "models/${LocalModelInterface.MODEL_FILENAME}")
            if (fallback.exists()) {
                return@withContext doLoad(fallback.absolutePath)
            }
            Log.w("LocalModel", "Model not found, using rule-based fallback")
            isLoaded = true
            true
        } else {
            doLoad(file.absolutePath)
        }
    }

    private fun doLoad(path: String): Boolean {
        return if (nativeAvailable) {
            isLoaded = nativeLoadModel(path)
            isLoaded
        } else {
            isLoaded = true
            true
        }
    }

    override suspend fun unloadModel() {
        if (nativeAvailable && isLoaded) {
            nativeUnloadModel()
        }
        isLoaded = false
    }

    override suspend fun parsePaymentText(rawText: String): ParsedTransaction {
        return withContext(Dispatchers.Default) {
            if (isLoaded && nativeAvailable) {
                val result = nativeInference(buildPrompt("parse", rawText), 128)
                LLMResponseParser.parseTransactionJson(result)
                    ?: RuleBasedParser.parsePaymentText(rawText)
            } else {
                RuleBasedParser.parsePaymentText(rawText)
            }
        }
    }

    override suspend fun classifyTransaction(
        merchant: String?,
        note: String?,
        amount: Long?,
        existingCategories: List<String>
    ): ClassificationResult {
        return withContext(Dispatchers.Default) {
            if (isLoaded && nativeAvailable) {
                val input = buildString {
                    append("Merchant: $merchant\n")
                    append("Note: $note\n")
                    append("Amount: ${amount?.let { "%.2f".format(it / 100.0) } ?: "unknown"}\n")
                    append("Available: ${existingCategories.joinToString(", ")}")
                }
                val result = nativeInference(buildPrompt("classify", input), 64)
                LLMResponseParser.parseClassificationJson(result)
                    ?: RuleBasedClassifier.classify(merchant, note)
            } else {
                RuleBasedClassifier.classify(merchant, note)
            }
        }
    }

    override suspend fun generateSavingTips(
        monthlyExpenseByCategory: Map<String, Long>,
        totalMonthlyExpense: Long,
        totalMonthlyIncome: Long
    ): List<SavingTip> {
        return withContext(Dispatchers.Default) {
            if (totalMonthlyExpense <= 0) return@withContext emptyList()
            val tips = mutableListOf<SavingTip>()
            val sorted = monthlyExpenseByCategory.entries
                .sortedByDescending { it.value }.take(3)

            sorted.forEach { (category, amount) ->
                val pct = amount.toFloat() / totalMonthlyExpense
                if (pct > 0.3f) {
                    val tip = when (category) {
                        "餐饮" -> SavingTip(
                            "餐饮支出偏高",
                            "本月餐饮占${"%.0f".format(pct * 100)}%，建议减少外卖频次",
                            (amount * 0.2).toLong(), category
                        )
                        "交通" -> SavingTip(
                            "交通支出可优化",
                            "本月交通占${"%.0f".format(pct * 100)}%，考虑月票或骑行",
                            (amount * 0.15).toLong(), category
                        )
                        "购物" -> SavingTip(
                            "购物支出较高",
                            "本月购物占${"%.0f".format(pct * 100)}%，建议设置冷静期",
                            (amount * 0.25).toLong(), category
                        )
                        "娱乐" -> SavingTip(
                            "娱乐消费可控制",
                            "本月娱乐占${"%.0f".format(pct * 100)}%，尝试免费替代方案",
                            (amount * 0.2).toLong(), category
                        )
                        else -> SavingTip(
                            "${category}支出占比高",
                            "本月${category}占${"%.0f".format(pct * 100)}%，建议评估必要性",
                            (amount * 0.1).toLong(), category
                        )
                    }
                    tips.add(tip)
                }
            }
            if (totalMonthlyIncome > 0 && totalMonthlyExpense > totalMonthlyIncome) {
                tips.add(SavingTip(
                    "入不敷出",
                    "超支${"%.0f".format((totalMonthlyExpense - totalMonthlyIncome) / 100.0)}元，从最大支出项削减",
                    totalMonthlyExpense - totalMonthlyIncome, null
                ))
            }
            tips
        }
    }

    private fun buildPrompt(task: String, input: String): String {
        val sysMsg = when (task) {
            "parse" -> "你是一个支付信息提取助手。从用户提供的文本中提取金额、商户、备注信息，返回JSON格式。"
            "classify" -> "你是一个消费分类助手。根据商户名称和备注将交易分类到合适的类别，返回JSON。"
            else -> ""
        }
        return "<|im_start|>system\n$sysMsg\n<|im_end|>\n<|im_start|>user\n$input\n<|im_end|>\n<|im_start|>assistant\n"
    }
}
