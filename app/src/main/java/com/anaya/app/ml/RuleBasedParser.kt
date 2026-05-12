package com.anaya.app.ml

import java.util.regex.Pattern

object RuleBasedParser {
    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("""[¥￥]?\s*(\d+(?:\.\d{1,2})?)\s*元?"""),
        Pattern.compile("""金额[：:]?\s*(\d+(?:\.\d{1,2})?)"""),
        Pattern.compile("""(\d+\.\d{2})\s*元"""),
        Pattern.compile("""[+-]?\s*(\d+(?:\.\d{1,2})?)(?=\s*元)""")
    )

    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("""在[（(]?\s*(.+?)\s*[)）]?消费"""),
        Pattern.compile("""向[（(]?\s*(.+?)\s*[)）]?付款"""),
        Pattern.compile("""商户[：:]?\s*(.+)"""),
        Pattern.compile("""收款方[：:]?\s*(.+)"""),
        Pattern.compile("""支付给[：:]?\s*(.+?)(?:\s|$)"""),
        Pattern.compile("""(?:支付宝|微信)\s*[—\-]\s*(.+)""")
    )

    fun parsePaymentText(rawText: String): ParsedTransaction {
        val text = rawText.trim()
        val amount = extractAmount(text)
        val merchant = extractMerchant(text)
        val note = text.replace("\n", " ").take(80)
        val confidence = when {
            amount != null && merchant != null -> 0.8f
            amount != null -> 0.5f
            else -> 0.2f
        }
        return ParsedTransaction(
            amount = amount,
            merchant = merchant,
            note = if (note.length <= 40) note else note.take(40) + "...",
            confidence = confidence
        )
    }

    private fun extractAmount(text: String): Long? {
        for (pattern in AMOUNT_PATTERNS) {
            val m = pattern.matcher(text)
            if (m.find()) {
                val v = m.group(1)?.toDoubleOrNull() ?: continue
                if (v in 0.01..999_999.0) return (v * 100).toLong()
            }
        }
        return null
    }

    private fun extractMerchant(text: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val m = pattern.matcher(text)
            if (m.find()) return m.group(1)?.trim()?.take(20)
        }
        return null
    }
}
