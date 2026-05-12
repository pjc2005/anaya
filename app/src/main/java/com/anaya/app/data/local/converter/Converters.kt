package com.anaya.app.data.local.converter

import androidx.room.TypeConverter
import org.json.JSONArray
import java.util.Date

class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { JSONArray(it).toString() }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            val array = JSONArray(it)
            (0 until array.length()).map { index -> array.getString(index) }
        }
    }
}
