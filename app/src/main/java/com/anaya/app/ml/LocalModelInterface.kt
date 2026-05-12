package com.anaya.app.ml

data class ParsedTransaction(
    val amount: Long? = null,
    val merchant: String? = null,
    val categoryName: String? = null,
    val note: String? = null,
    val type: String? = null,       // EXPENSE / INCOME / TRANSFER（模型推断）
    val dateMs: Long? = null,       // 交易时间（毫秒），模型推断或启发式解析
    val confidence: Float = 0f
)

data class ClassificationResult(
    val categoryId: Long? = null,
    val confidence: Float = 0f,
    val explanation: String? = null
)

data class SavingTip(
    val title: String,
    val description: String,
    val estimatedSavings: Long? = null,
    val category: String? = null
)

interface LocalModelInterface {
    fun isModelLoaded(): Boolean
    suspend fun loadModel(modelPath: String): Boolean
    suspend fun unloadModel()
    suspend fun parsePaymentText(rawText: String): ParsedTransaction
    suspend fun classifyTransaction(
        merchant: String?,
        note: String?,
        amount: Long?,
        existingCategories: List<String>
    ): ClassificationResult
    /**
     * 从任意格式的文本中批量提取交易记录。
     * 输入可以是 CSV、TSV、银行对账单、其他 App 导出文本等。
     */
    suspend fun extractTransactions(rawText: String): List<ParsedTransaction>

    suspend fun generateSavingTips(
        monthlyExpenseByCategory: Map<String, Long>,
        totalMonthlyExpense: Long,
        totalMonthlyIncome: Long
    ): List<SavingTip>

    companion object {
        const val MODEL_FILENAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    }
}
