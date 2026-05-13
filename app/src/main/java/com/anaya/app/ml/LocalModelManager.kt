package com.anaya.app.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelBackend { NONE, NATIVE, HTTP }
enum class ModelStatus { NotDownloaded, Downloading, Ready, Error }

/**
 * 本地模型管理器 — 支持三种推理后端：
 *
 * 1. HTTP 后端：连接 llama-server / 任意 OpenAI 兼容 API
 *    - 默认指向本机 Qwen3.5-9B（192.168.0.113:8081）
 *    - 也可指向手机上运行的 0.5B llama-server
 *
 * 2. 原生后端（NATIVE）：通过 JNI 加载 libllama.so（需打包进 APK）
 *    - 用于手机上直接跑 0.5B GGUF 模型
 *    - 目前.so 未打包，nativeAvailable=false
 *
 * 3. 规则回退：HTTP 和后端都不可用时用 RuleBasedParser
 */
@Singleton
class LocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalModelInterface {

    companion object {
        /** HTTP 后端地址，可在设置中修改 */
        var httpApiUrl: String = "http://192.168.0.113:8081/v1/chat/completions"
        var httpApiKey: String = "not-needed"
        /** HTTP 请求超时（秒） */
        var httpTimeout: Int = 10
    }

    private var isLoaded = false
    private var nativeAvailable = false
    private var httpAvailable = false

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
            Log.i("LocalModel", "Native library not available: ${e.message}")
        }

        // 探测 HTTP 后端是否可用
        try {
            val conn = URL(httpApiUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.requestMethod = "HEAD"
            conn.connect()
            httpAvailable = conn.responseCode == 200 || conn.responseCode == 405
            conn.disconnect()
        } catch (_: Exception) {
            httpAvailable = false
        }
        Log.i("LocalModel", "HTTP backend at $httpApiUrl available=$httpAvailable")
    }

    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeInference(prompt: String, maxTokens: Int): String

    override fun isModelLoaded(): Boolean = isLoaded || httpAvailable

    val activeBackend: ModelBackend
        get() = when {
            httpAvailable -> ModelBackend.HTTP
            nativeAvailable && isLoaded -> ModelBackend.NATIVE
            else -> ModelBackend.NONE
        }

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
        if (httpAvailable) {
            isLoaded = true
            return@withContext true
        }
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
            isLoaded = httpAvailable
            httpAvailable
        }
    }

    override suspend fun unloadModel() {
        if (nativeAvailable && isLoaded) {
            nativeUnloadModel()
        }
        isLoaded = false
    }

    // ════════════════════════════════════════════════
    //  LLM 推理入口
    // ════════════════════════════════════════════════

    override suspend fun parsePaymentText(rawText: String): ParsedTransaction {
        return withContext(Dispatchers.Default) {
            // 1) HTTP 后端（优先，可用性最高）
            val httpResult = inferHttp("parse", rawText)
            if (httpResult != null) {
                Log.i("LocalModel", "HTTP parse OK: amount=${httpResult.amount} confidence=${httpResult.confidence}")
                return@withContext httpResult
            }
            // 2) 原生 JNI 后端
            if (nativeAvailable && isLoaded) {
                try {
                    val result = nativeInference(buildPrompt("parse", rawText), 128)
                    val parsed = LLMResponseParser.parseTransactionJson(result)
                    if (parsed != null) return@withContext parsed
                } catch (e: Exception) {
                    Log.w("LocalModel", "Native parse failed", e)
                }
            }
            // 3) 规则回退
            RuleBasedParser.parsePaymentText(rawText)
        }
    }

    override suspend fun classifyTransaction(
        merchant: String?,
        note: String?,
        amount: Long?,
        existingCategories: List<String>
    ): ClassificationResult {
        return withContext(Dispatchers.Default) {
            val input = buildString {
                append("Merchant: $merchant\n")
                append("Note: $note\n")
                append("Amount: ${amount?.let { "%.2f".format(it / 100.0) } ?: "unknown"}\n")
                append("Available: ${existingCategories.joinToString(", ")}")
            }

            // 1) HTTP 后端
            val httpResult = inferHttp("classify", input)
            if (httpResult != null) return@withContext ClassificationResult(
                categoryId = null,
                confidence = httpResult.confidence,
                explanation = httpResult.categoryName
            )

            // 2) 原生 JNI
            if (nativeAvailable && isLoaded) {
                try {
                    val result = nativeInference(buildPrompt("classify", input), 64)
                    val classified = LLMResponseParser.parseClassificationJson(result)
                    if (classified != null) return@withContext classified
                } catch (_: Exception) {}
            }

            // 3) 规则回退
            RuleBasedClassifier.classify(merchant, note)
        }
    }

    override suspend fun extractTransactions(rawText: String): List<ParsedTransaction> {
        return withContext(Dispatchers.Default) {
            val truncated = if (rawText.length > 6000) {
                rawText.take(6000) + "\n...(文件较长，已截取前6000字符)"
            } else rawText

            // 1) HTTP
            val httpResult = inferHttp("extract", truncated)
            if (httpResult != null && httpResult.amount != null) {
                return@withContext listOf(httpResult)
            }

            // 2) JNI
            if (nativeAvailable && isLoaded) {
                try {
                    val result = nativeInference(buildPrompt("extract", truncated), 1024)
                    val list = LLMResponseParser.parseTransactionList(result)
                    if (list != null) return@withContext list
                } catch (_: Exception) {}
            }

            // 3) 规则回退
            fallbackParseTransactions(rawText)
        }
    }

    override suspend fun generateSavingTips(
        monthlyExpenseByCategory: Map<String, Long>,
        totalMonthlyExpense: Long,
        totalMonthlyIncome: Long
    ): List<SavingTip> {
        // 使用现有的硬编码储蓄建议逻辑
        return withContext(Dispatchers.Default) {
            if (totalMonthlyExpense <= 0) return@withContext emptyList()
            val tips = mutableListOf<SavingTip>()
            val sorted = monthlyExpenseByCategory.entries
                .sortedByDescending { it.value }.take(3)

            sorted.forEach { (category, amount) ->
                val pct = amount.toFloat() / totalMonthlyExpense
                if (pct > 0.3f) {
                    val tip = when (category) {
                        "餐饮" -> SavingTip("餐饮支出偏高", "本月餐饮占${"%.0f".format(pct * 100)}%，建议减少外卖频次", (amount * 0.2).toLong(), category)
                        "交通" -> SavingTip("交通支出可优化", "本月交通占${"%.0f".format(pct * 100)}%，考虑月票或骑行", (amount * 0.15).toLong(), category)
                        "购物" -> SavingTip("购物支出较高", "本月购物占${"%.0f".format(pct * 100)}%，建议设置冷静期", (amount * 0.25).toLong(), category)
                        "娱乐" -> SavingTip("娱乐消费可控制", "本月娱乐占${"%.0f".format(pct * 100)}%，尝试免费替代方案", (amount * 0.2).toLong(), category)
                        else -> SavingTip("${category}支出占比高", "本月${category}占${"%.0f".format(pct * 100)}%，建议评估必要性", (amount * 0.1).toLong(), category)
                    }
                    tips.add(tip)
                }
            }
            if (totalMonthlyIncome > 0 && totalMonthlyExpense > totalMonthlyIncome) {
                tips.add(SavingTip("入不敷出", "超支${"%.0f".format((totalMonthlyExpense - totalMonthlyIncome) / 100.0)}元，从最大支出项削减", totalMonthlyExpense - totalMonthlyIncome, null))
            }
            tips
        }
    }

    // ════════════════════════════════════════════════
    //  HTTP 推理
    // ════════════════════════════════════════════════

    /**
     * 通过 HTTP API 调用本地模型（llama-server / ollama / 任意 OpenAI 兼容接口）
     * @return 解析成功的 ParsedTransaction，失败返回 null
     */
    private suspend fun inferHttp(task: String, input: String): ParsedTransaction? {
        if (!httpAvailable) return null
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("model", "local")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", getSystemPrompt(task))
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", input)
                        })
                    })
                    put("temperature", 0.1)
                    put("max_tokens", when (task) {
                        "parse" -> 128
                        "classify" -> 64
                        "extract" -> 512
                        else -> 128
                    })
                    put("stream", false)
                }

                val conn = URL(httpApiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = httpTimeout * 1000
                conn.readTimeout = httpTimeout * 1000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $httpApiKey")

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    Log.w("LocalModel", "HTTP $responseCode for task=$task")
                    return@withContext null
                }

                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val json = JSONObject(responseText)
                val content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                conn.disconnect()

                when (task) {
                    "parse" -> LLMResponseParser.parseTransactionJson(content)
                    "classify" -> {
                        val cls = LLMResponseParser.parseClassificationJson(content)
                        cls?.let {
                            ParsedTransaction(
                                categoryName = it.explanation,
                                confidence = it.confidence
                            )
                        }
                    }
                    "extract" -> {
                        val list = LLMResponseParser.parseTransactionList(content)
                        list?.firstOrNull()
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.w("LocalModel", "HTTP infer failed for task=$task", e)
                httpAvailable = false
                null
            }
        }
    }

    private fun getSystemPrompt(task: String): String = when (task) {
        "parse" -> "你是一个支付信息提取助手。分析用户提供的支付页面文本，提取金额（元）、商户名称、交易类型、推荐分类。返回JSON: {\"amount\": 1250, \"merchant\": \"星巴克\", \"type\": \"EXPENSE\", \"category\": \"餐饮\", \"note\": \"...\", \"confidence\": 0.9}。amount以分为单位（元×100）。只输出JSON。"
        "classify" -> "你是一个消费分类助手。根据商户名称、备注和金额，判断这笔交易属于哪个分类。只返回JSON: {\"category\": \"餐饮/外卖\", \"confidence\": 0.85}"
        "extract" -> "你是记账助手。从用户提供的文本中提取交易信息，返回JSON: {\"amount\": 1250, \"merchant\": \"...\", \"type\": \"EXPENSE\", \"category\": \"...\", \"note\": \"...\", \"confidence\": 0.9}"
        else -> ""
    }

    // ════════════════════════════════════════════════
    //  辅助
    // ════════════════════════════════════════════════

    private fun fallbackParseTransactions(rawText: String): List<ParsedTransaction> {
        val results = mutableListOf<ParsedTransaction>()
        val lines = rawText.lines().filter { it.isNotBlank() }
        for (line in lines) {
            if (line.contains("金额") || line.contains("日期") || line.contains("商户")) continue
            val parsed = RuleBasedParser.parsePaymentText(line)
            if (parsed.amount != null && parsed.amount > 0) {
                results.add(parsed)
            }
        }
        return results
    }

    private fun buildPrompt(task: String, input: String): String {
        val sysMsg = when (task) {
            "parse" -> "你是一个支付信息提取助手。从用户提供的文本中提取金额、商户、备注信息，返回JSON格式。"
            "classify" -> "你是一个消费分类助手。根据商户名称和备注将交易分类到合适的类别，返回JSON。"
            "extract" -> "你是一个记账数据导入助手。你收到的文本是其他记账App导出的交易记录（可能是CSV、TSV或纯文本格式）。请分析并提取所有交易记录，按以下JSON数组格式输出..."
            else -> ""
        }
        return "<|im_start|>system\n$sysMsg\n<|im_end|>\\n<|im_start|>user\n$input\n<|im_end|>\\n<|im_start|>assistant\n"
    }
}
