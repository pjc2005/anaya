package com.anaya.app.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {

    fun formatAmount(cents: Long): String {
        val absCents = kotlin.math.abs(cents)
        val yuan = absCents / 100
        val jiao = (absCents % 100) / 10
        val fen = absCents % 10
        return "$yuan.$jiao$fen"
    }

    fun formatAmountWithSymbol(cents: Long): String {
        return "¥${formatAmount(cents)}"
    }

    fun centsToDisplayString(cents: Long): String {
        if (cents == 0L) return "0"
        val yuan = cents / 100
        val remainder = cents % 100
        return if (remainder == 0L) {
            yuan.toString()
        } else {
            "$yuan.${String.format("%02d", remainder)}".trimEnd('0').trimEnd('.')
        }
    }

    fun displayStringToCents(display: String): Long {
        return try {
            val clean = display.trim().replace(",", "")
            if (clean.isEmpty() || clean == "0" || clean == "0.") return 0L
            (clean.toDouble() * 100).toLong()
        } catch (e: NumberFormatException) {
            0L
        }
    }
}
