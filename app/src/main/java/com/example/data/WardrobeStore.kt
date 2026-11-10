package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WardrobeStore(context: Context) {
    private val prefs = context.getSharedPreferences("wardrobe", Context.MODE_PRIVATE)

    data class WardrobeItem(
        val id: String,
        val name: String,
        val category: GarmentCategory,
        val uriString: String
    )

    suspend fun addItem(name: String, category: GarmentCategory, uri: Uri) = withContext(Dispatchers.IO) {
        val items = getItems().toMutableList()
        items.add(WardrobeItem(
            id = System.currentTimeMillis().toString(),
            name = name,
            category = category,
            uriString = uri.toString()
        ))
        saveItems(items)
    }

    suspend fun getItems(): List<WardrobeItem> = withContext(Dispatchers.IO) {
        val json = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(json)
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            WardrobeItem(
                id = obj.getString("id"),
                name = obj.getString("name"),
                category = GarmentCategory.valueOf(obj.getString("category")),
                uriString = obj.getString("uri")
            )
        }
    }

    private fun saveItems(items: List<WardrobeItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("category", item.category.name)
                put("uri", item.uriString)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
