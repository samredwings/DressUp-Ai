package com.example.data

import android.content.Context
import java.io.File

class LocalModelStore(context: Context) {
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    fun getModelsDir(): File = modelsDir

    fun listDownloadedModels(): List<File> {
        return modelsDir.listFiles()?.filter { it.extension == "onnx" || it.extension == "tflite" } ?: emptyList()
    }
}
