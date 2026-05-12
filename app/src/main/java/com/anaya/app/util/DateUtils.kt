package com.anaya.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun getMonthStartMillis(year: Int, month: Int): Long {
    val cal = Calendar.getInstance()
    cal.set(year, month - 1, 1, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun getMonthEndMillis(year: Int, month: Int): Long {
    val cal = Calendar.getInstance()
    cal.set(year, month - 1, 1, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.MONTH, 1)
    cal.add(Calendar.MILLISECOND, -1)
    return cal.timeInMillis
}

fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun formatDateGroupHeader(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()

    when {
        isSameDay(cal, now) -> return "今天"
        isSameDay(cal, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) -> return "昨天"
    }

    val calNow = Calendar.getInstance()
    return if (cal.get(Calendar.YEAR) == calNow.get(Calendar.YEAR)) {
        "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
    } else {
        "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
    }
}

fun formatDateShort(timestamp: Long): String {
    val sdf = SimpleDateFormat("M月d日", Locale.CHINA)
    return sdf.format(Date(timestamp))
}

fun formatDateYearMonth(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy年M月", Locale.CHINA)
    return sdf.format(Date(timestamp))
}

fun getTodayStartMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
