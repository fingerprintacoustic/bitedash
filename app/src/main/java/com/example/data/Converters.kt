package com.example.data

import androidx.room.TypeConverter
import com.example.model.MenuItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val menuItemListType: java.lang.reflect.Type = Types.newParameterizedType(List::class.java, MenuItem::class.java)
    private val adapter = moshi.adapter<List<MenuItem>>(menuItemListType)

    @TypeConverter
    fun fromMenuItemList(value: List<MenuItem>?): String? {
        return value?.let { adapter.toJson(it) }
    }

    @TypeConverter
    fun toMenuItemList(value: String?): List<MenuItem>? {
        if (value == null) return emptyList()
        return try {
            adapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
