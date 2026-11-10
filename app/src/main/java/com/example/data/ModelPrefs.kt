package com.example.data

import android.content.Context

class ModelPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)

    var selectedModelPath: String?
        get() = prefs.getString("selected_model_path", null)
        set(value) = prefs.edit().putString("selected_model_path", value).apply()

    var useGpu: Boolean
        get() = prefs.getBoolean("use_gpu", true)
        set(value) = prefs.edit().putBoolean("use_gpu", value).apply()
}
