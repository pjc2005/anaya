package com.anaya.app.ml

data class ParsedTransaction(
    val amount: Long? = null,
    val merchant: String? = null,
    val categoryName: String? = null,
    val note: String? = null,
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
    suspend fun generateSavingTips(
        monthlyExpenseByCategory: Map<String, Long>,
        totalMonthlyExpense: Long,
        totalMonthlyIncome: Long
    ): List<SavingTip>

    companion object {
        const val MODEL_FILENAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    }
}
