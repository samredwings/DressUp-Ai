package com.example.data

import java.io.File

enum class LocalModelFormat {
    LITERT_LM, GGUF
}

data class LocalModel(
    val name: String,
    val format: LocalModelFormat,
    val file: File
)
