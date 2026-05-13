package com.anaya.app.ml

import java.util.regex.Pattern

object RuleBasedParser {
    // Amount patterns: try exact decimal first, then fallback to general
    private val AMOUNT_PATTERNS = listOf(
        // Prefer patterns with explicit decimal
        Pattern.compile("""[¥￥$€£]?\s*(\d+\.\d{2})\s*元?"""),
        Pattern.compile("""金额[：:]?\s*(\d+\.\d{2})"""),
        Pattern.compile("""(\d+\.\d{2})\s*元"""),
        // Fallback to patterns with optional decimal
        Pattern.compile("""[¥￥$€£]?\s*(\d+(?:\.\d{1,2})?)\s*元?"""),
        Pattern.compile("""金额[：:]?\s*(\d+(?:\.\d{1,2})?)"""),
        Pattern.compile("""[+-]?\s*(\d+(?:\.\d{1,2})?)(?=\s*元)""")
    )

    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("""在[（(]?\s*(.+?)\s*[)）]?消费"""),
        Pattern.compile("""向[（(]?\s*(.+?)\s*[)）]?付款"""),
        Pattern.compile("""商户[：:]?\s*(.+)"""),
        Pattern.compile("""收款方[：:]?\s*(.+)"""),
        Pattern.compile("""支付给[：:]?\s*(.+?)(?:\s|$)"""),
        Pattern.compile("""(?:支付宝|微信)\s*[—\-]\s*(.+)"""),
        Pattern.compile("""转给[：:]?\s*(.+?)(?:\s|$)"""),
        Pattern.compile("""对方[：:]?\s*(.+?)(?:\s|$)"""),
        Pattern.compile("""收入[：:]?\s*¥?\s*\d+(\.\d{1,2})?\s*[—\-]\s*(.+)""")
    )

    /** Strip time patterns HH:MM or HH:MM:SS before amount matching */
    private val TIME_PATTERN = Pattern.compile("""\b\d{1,2}:\d{2}(:\d{2})?\b""")

    /** Strip quantity patterns like "2件", "3个", "5笔" */
    private val QUANTITY_PATTERN = Pattern.compile("""\b\d+\s*[件个笔单]\b""")

    /** Year numbers (2020-2035 without decimal) — filter out */
    private val YEAR_RANGE = 2020..2035

    fun parsePaymentText(rawText: String): ParsedTransaction {
        val cleaned = rawText.trim().let { text ->
            joinSplitAmountNodes(text)
                .let { TIME_PATTERN.matcher(it).replaceAll(" ") }
                .let { QUANTITY_PATTERN.matcher(it).replaceAll(" ") }
        }

        val amount = extractAmount(cleaned)
        val merchant = extractMerchant(cleaned)
        val note = cleaned.replace("\n", " ").take(80)
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

    /**
     * Try to join split amount nodes from different accessibility views.
     * Handles: "12\n.34" → "12.34",  "12\n.\n34" → "12.34",  "12.\n34" → "12.34"
     */
    fun joinSplitAmountNodes(text: String): String {
        return text
            .replace(Regex("""(\d+)\s*\n\s*\.(\d{1,2})"""), "$1.$2")
            .replace(Regex("""(\d+)\s*\n\s*\.\s*\n\s*(\d{1,2})"""), "$1.$2")
            .replace(Regex("""(\d+)\.\s*\n\s*(\d{1,2})"""), "$1.$2")
    }

    private fun extractAmount(text: String): Long? {
        val candidates = mutableListOf<Pair<Long, Boolean>>() // (amount in cents, hadDecimal)

        for (pattern in AMOUNT_PATTERNS) {
            val m = pattern.matcher(text)
            while (m.find()) {
                val raw = m.group(1) ?: continue
                val v = raw.toDoubleOrNull() ?: continue
                if (v !in 0.01..999_999.0) continue

                val hasDecimal = raw.contains(".")
                // Filter out standalone year numbers (2020-2035 without cents)
                if (!hasDecimal && v.toInt() in YEAR_RANGE) continue

                candidates.add((v * 100).toLong() to hasDecimal)
            }
        }

        if (candidates.isEmpty()) return null

        // Prefer amounts with explicit decimal point (more likely real payments)
        val withDecimal = candidates.filter { it.second }
        if (withDecimal.isNotEmpty()) return withDecimal.first().first

        // No decimal amounts — return the smallest round amount (least likely noise)
        return candidates.minBy { it.first }.first
    }

    private fun extractMerchant(text: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val m = pattern.matcher(text)
            if (m.find()) return m.group(1)?.trim()?.take(20)
        }
        return null
    }
}
