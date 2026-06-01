package com.unnamed.app.data.local

import androidx.room.TypeConverter

/** Room type converters. Stores List<String> as a newline-joined string. */
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        value?.joinToString("\n") ?: ""

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split("\n")
}
