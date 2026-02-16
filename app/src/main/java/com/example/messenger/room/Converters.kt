package com.example.messenger.room

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // List<String>
    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<String>?): String? {
        return gson.toJson(list)
    }

    // List<Int>
    @TypeConverter
    fun fromIntList(value: String?): List<Int>? {
        if (value == null) return null
        val type = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun intListToString(list: List<Int>?): String? {
        return if (list == null) null else gson.toJson(list)
    }
}