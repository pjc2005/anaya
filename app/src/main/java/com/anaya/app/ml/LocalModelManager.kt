package com.anaya.app.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

enum class LlamaServerStatus {
    /** 尚未初始化 */
    Unknown,
    /** 正在解压资产并启动服务 */
    Starting,
    /** 服务运行中 /v1/chat/completions 可正常响应 */
    Running,
    /** 启动失败（二进制缺失、模型文件缺失、端口占用等） */
    Failed,
    /** 已停止 */
    Stopped
}

/**
 * 旧版 ModelStatus — 兼容 SetupScreen/SettingsScreen UI 层
 * 新代码请使用 LlamaServerStatus
 */
enum class ModelStatus { NotDownloaded, Downloading, Ready, Error }

/**
 * 本地模型管理器 — 纯手机端 0.5B 推理
 *
 * 架构：
 *  - APK assets 中打包了 arm64-v8a 的 llama-server 二进制 和 Qwen2.5-0.5B GGUF
 *  - 首次使用时解压到 filesDir，启动 llama-server 子进程监听 127.0.0.1:8081
 *  - 所有推理通过 HTTP 调用本地 server 完成
 *  - 无需接电脑、无需 JNI、无需网络
 */
@Singleton
class LocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalModelInterface {

    companion object {
        private const val TAG = "LocalModel"
        private const val SERVER_PORT = 8081
        private const val ASSETS_BIN = "llama/llama-server"
        private const val ASSETS_MODEL = "llama/qwen2.5-0.5b-instruct-q4_k_m.gguf"

        /** 本地 server 的 OpenAI 兼容 API 地址 */
        val httpApiUrl: String
            get() = "http://127.0.0.1:$SERVER_PORT/v1/chat/completions"

        /** 模型在 filesDir 中存放的目录 */
        fun getModelDir(context: Context): String =
            context.filesDir.resolve("llama").absolutePath

        /** 模型的完整路径 */
        fun getModelFilePath(context: Context): String =
            File(getModelDir(context), LocalModelInterface.MODEL_FILENAME).absolutePath

        /** llama-server 二进制在 filesDir 中的路径 */
        fun getBinPath(context: Context): String =
            File(getModelDir(context), "llama-server").absolutePath
    }

    private val _serverStatus = MutableStateFlow(LlamaServerStatus.Unknown)
    val serverStatus: StateFlow<LlamaServerStatus> = _serverStatus.asStateFlow()

    // 向下兼容 — UI 层（SetupScreen/SettingsScreen）使用旧的 ModelStatus
    private val _modelStatus = MutableStateFlow(ModelStatus.NotDownloaded)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()
    private val _downloadProgress = MutableStateFlow(100)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private var serverProcess: Process? = null
    private var httpAvailable = false

    // ───────────────────────────────────────────────
    //  公共 API
    // ───────────────────────────────────────────────

    override fun isModelLoaded(): Boolean = httpAvailable

    /**
     * 确保本地 llama-server 正在运行。
     * 首次调用时会从 assets 解压二进制和模型，然后启动 server。
     */
    suspend fun ensureServerRunning() {
        if (httpAvailable) return
        if (_serverStatus.value == LlamaServerStatus.Starting) return
        _modelStatus.value = ModelStatus.Downloading
        _downloadProgress.value = 50
        _serverStatus.value = LlamaServerStatus.Starting
        val ok = withContext(Dispatchers.IO) {
            try {
                extractAssets()
                _downloadProgress.value = 80
                startServerProcess()
                waitForServer()
            } catch (e: Exception) {
                Log.e(TAG, "Server startup failed", e)
                false
            }
        }
        if (ok) {
            httpAvailable = true
            _serverStatus.value = LlamaServerStatus.Running
            _modelStatus.value = ModelStatus.Ready
            _downloadProgress.value = 100
            Log.i(TAG, "Local llama-server is ready on $httpApiUrl")
        } else {
            _serverStatus.value = LlamaServerStatus.Failed
            _modelStatus.value = ModelStatus.Error
        }
    }

    /** 停止本地 server */
    fun stopServer() {
        try {
            serverProcess?.destroy()
            serverProcess?.waitFor()
        } catch (_: Exception) {}
        serverProcess = null
        httpAvailable = false
        _serverStatus.value = LlamaServerStatus.Stopped
        Log.i(TAG, "Local llama-server stopped")
    }

    override suspend fun loadModel(modelPath: String): Boolean {
        ensureServerRunning()
        return httpAvailable
    }

    override suspend fun unloadModel() {
        stopServer()
    }

    /**
     * 检查模型状态 — 兼容旧 UI 调用
     */
    fun checkModelStatus() {
        val status = when {
            httpAvailable -> ModelStatus.Ready
            _serverStatus.value == LlamaServerStatus.Starting -> ModelStatus.Downloading
            // 模型文件已在磁盘上（资产已解压）→ 服务正在 AnayaApp 启动中
            File(getModelFilePath(context)).exists() -> ModelStatus.Downloading
            else -> ModelStatus.NotDownloaded
        }
        _modelStatus.value = status
    }

    /**
     * 下载模型 — 已打包在 APK 中，仅做兼容
     */
    suspend fun downloadModel() {
        ensureServerRunning()
    }

    // ───────────────────────────────────────────────
    //  资产解压
    // ───────────────────────────────────────────────

    private fun extractAssets() {
        val dir = File(getModelDir(context))
        dir.mkdirs()

        // 解压 llama-server 二进制
        val binFile = File(getBinPath(context))
        if (!binFile.exists() || binFile.length() == 0L) {
            Log.i(TAG, "Extracting llama-server binary...")
            context.assets.open(ASSETS_BIN).use { input ->
                binFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            binFile.setExecutable(true)
            Log.i(TAG, "Binary extracted: ${binFile.length()} bytes")
        }

        // 解压 GGUF 模型
        val modelFile = File(getModelFilePath(context))
        if (!modelFile.exists() || modelFile.length() == 0L) {
            Log.i(TAG, "Extracting model file...")
            context.assets.open(ASSETS_MODEL).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Model extracted: ${modelFile.length()} bytes")
        }
    }

    // ───────────────────────────────────────────────
    //  子进程管理
    // ───────────────────────────────────────────────

    private fun startServerProcess(): Boolean {
        val bin = getBinPath(context)
        val model = getModelFilePath(context)

        if (!File(bin).exists()) {
            Log.e(TAG, "llama-server binary not found at $bin")
            return false
        }
        if (!File(model).exists()) {
            Log.e(TAG, "Model file not found at $model")
            return false
        }

        try {
            val pb = ProcessBuilder(
                bin,
                "-m", model,
                "--host", "127.0.0.1",
                "--port", SERVER_PORT.toString(),
                "--ctx-size", "1024",
                "--n-gpu-layers", "99",              // 纯 GPU 跑
                "--temp", "0.1",
                "--repeat-penalty", "1.0",
                "-ngl", "99",
                "--mlock"                             // 锁定内存防交换
            )
            pb.directory(File(getModelDir(context)))
            pb.redirectErrorStream(true)

            serverProcess = pb.start()
            Log.i(TAG, "llama-server started")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start llama-server", e)
            return false
        }
    }

    /** 轮询 /health 等待 server 就绪 */
    private suspend fun waitForServer(): Boolean {
        val healthUrl = "http://127.0.0.1:$SERVER_PORT/health"
        val startMs = System.currentTimeMillis()
        val timeoutMs = 30_000L // 30 秒超时

        while (System.currentTimeMillis() - startMs < timeoutMs) {
            try {
                val conn = URL(healthUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                if (code == 200) {
                    Log.i(TAG, "Server ready after ${System.currentTimeMillis() - startMs}ms")
                    return true
                }
            } catch (_: Exception) {
                // server 还没就绪，继续等
            }
            delay(500)
        }

        // 如果 health 不可用，尝试 /v1/models
        try {
            val conn = URL("http://127.0.0.1:$SERVER_PORT/v1/models").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            conn.disconnect()
            if (code == 200) {
                Log.i(TAG, "Server ready (v1/models) after ${System.currentTimeMillis() - startMs}ms")
                return true
            }
        } catch (_: Exception) {}

        Log.w(TAG, "Server did not become ready within ${timeoutMs / 1000}s")
        return false
    }

    // ───────────────────────────────────────────────
    //  LLM 推理入口
    // ───────────────────────────────────────────────

    override suspend fun parsePaymentText(rawText: String): ParsedTransaction {
        return withContext(Dispatchers.Default) {
            ensureServerRunning()
            val httpResult = inferLocal("parse", rawText)
            if (httpResult != null) {
                Log.i(TAG, "Local LLM parse OK: amount=${httpResult.amount} confidence=${httpResult.confidence}")
                return@withContext httpResult
            }
            // 规则回退
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
            ensureServerRunning()
            val input = buildString {
                append("Merchant: $merchant\n")
                append("Note: $note\n")
                append("Amount: ${amount?.let { "%.2f".format(it / 100.0) } ?: "unknown"}\n")
                append("Available: ${existingCategories.joinToString(", ")}")
            }

            val httpResult = inferLocal("classify", input)
            if (httpResult != null) return@withContext ClassificationResult(
                categoryId = null,
                confidence = httpResult.confidence,
                explanation = httpResult.categoryName
            )

            RuleBasedClassifier.classify(merchant, note)
        }
    }

    override suspend fun extractTransactions(rawText: String): List<ParsedTransaction> {
        return withContext(Dispatchers.Default) {
            ensureServerRunning()
            val truncated = if (rawText.length > 6000) {
                rawText.take(6000) + "\n...(文件较长，已截取前6000字符)"
            } else rawText

            val httpResult = inferLocal("extract", truncated)
            if (httpResult != null && httpResult.amount != null) {
                return@withContext listOf(httpResult)
            }

            fallbackParseTransactions(rawText)
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

    // ───────────────────────────────────────────────
    //  本地 HTTP 推理
    // ───────────────────────────────────────────────

    /**
     * 通过本地 llama-server HTTP API 调用模型
     * @return 解析成功的 ParsedTransaction，失败返回 null
     */
    private suspend fun inferLocal(task: String, input: String): ParsedTransaction? {
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
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Content-Type", "application/json")

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    Log.w(TAG, "Local server HTTP $responseCode for task=$task")
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
                Log.w(TAG, "Local infer failed for task=$task", e)
                httpAvailable = false
                _serverStatus.value = LlamaServerStatus.Failed
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

    // ───────────────────────────────────────────────
    //  辅助
    // ───────────────────────────────────────────────

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
}
