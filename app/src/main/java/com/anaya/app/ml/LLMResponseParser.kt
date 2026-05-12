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
                type = obj.optString("type", "").takeIf { it in listOf("EXPENSE", "INCOME", "TRANSFER") },
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

    /**
     * 解析模型返回的交易列表 JSON 数组
     * 示例: [{"amount":12.5,"type":"EXPENSE","note":"午餐","merchant":"麦当劳","date":"2025-01-15","category":"餐饮"}, ...]
     */
    fun parseTransactionList(response: String): List<ParsedTransaction>? {
        return try {
            val arr = extractJsonArray(response) ?: return null
            val list = mutableListOf<ParsedTransaction>()
            val dateFormats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
            )
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val dateStr = obj.optString("date", "").takeIf { it.isNotBlank() }
                val dateMs = dateStr?.let { s ->
                    dateFormats.firstNotNullOfOrNull { fmt ->
                        try { fmt.parse(s)?.time } catch (_: Exception) { null }
                    }
                }
                list.add(ParsedTransaction(
                    amount = obj.optLong("amount", 0).takeIf { it > 0 },
                    merchant = obj.optString("merchant", "").takeIf { it.isNotBlank() },
                    categoryName = obj.optString("category", "").takeIf { it.isNotBlank() },
                    note = obj.optString("note", "").takeIf { it.isNotBlank() },
                    type = obj.optString("type", "").takeIf { it in listOf("EXPENSE", "INCOME", "TRANSFER") },
                    dateMs = dateMs,
                    confidence = obj.optDouble("confidence", 0.8).toFloat()
                ))
            }
            list.takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null }
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun extractJsonArray(text: String): org.json.JSONArray? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) {
            try { org.json.JSONArray(text.substring(start, end + 1)) } catch (_: Exception) { null }
        } else null
    }
}
