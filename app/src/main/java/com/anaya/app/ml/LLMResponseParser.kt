package com.anaya.app.ml

import org.json.JSONObject

object LLMResponseParser {
    fun parseTransactionJson(response: String): ParsedTransaction? {
        return try {
            val json = extractJson(response) ?: return null
            val obj = JSONObject(json)
            ParsedTransaction(
                amount = obj.optLong("amount", 0).takeIf { it > 0 },
                merchant = obj.optString("merchant", "").takeIf { it.isNotBlank() },
                note = obj.optString("note", "").takeIf { it.isNotBlank() },
                confidence = obj.optDouble("confidence", 0.0).toFloat()
            )
        } catch (e: Exception) { null }
    }

    fun parseClassificationJson(response: String): ClassificationResult? {
        return try {
            val json = extractJson(response) ?: return null
            val obj = JSONObject(json)
            ClassificationResult(
                confidence = obj.optDouble("confidence", 0.0).toFloat(),
                explanation = obj.optString("category", "").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) { null }
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }
}
