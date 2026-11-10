package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * MODEL MANAGER
 *
 * Manages on-device AI models for try-on processing.
 * Pure Android - no ONNX, no OpenCV dependencies.
 * Models are downloaded from HuggingFace/GitHub and stored locally.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"

        // Free model sources (direct download URLs)
        private val MODEL_SOURCES = mapOf(
            "mediapipe_pose" to "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task",
            "mediapipe_segmentation" to "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite"
        )

        data class ModelInfo(
            val name: String,
            val fileName: String,
            val sizeMb: Float,
            val category: ModelCategory,
            val description: String
        )

        enum class ModelCategory {
            POSE, SEGMENTATION, ENHANCEMENT
        }

        val SUPPORTED_MODELS = listOf(
            ModelInfo(
                name = "Pose Detector (Lite)",
                fileName = "pose_landmarker_lite.task",
                sizeMb = 5.5f,
                category = ModelCategory.POSE,
                description = "Lightweight pose detection for all devices"
            ),
            ModelInfo(
                name = "Selfie Segmentation",
                fileName = "selfie_segmentation.tflite",
                sizeMb = 0.2f,
                category = ModelCategory.SEGMENTATION,
                description = "Fast person segmentation"
            )
        )
    }

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // ---------------------------------------------------------------- public API

    fun getModelsDirectory(): File = modelsDir

    fun listDownloadedModels(): List<File> {
        return modelsDir.listFiles()?.filter { it.isFile } ?: emptyList()
    }

    fun isModelDownloaded(fileName: String): Boolean {
        return File(modelsDir, fileName).exists()
    }

    fun getModelPath(fileName: String): String? {
        val file = File(modelsDir, fileName)
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Downloads a model with progress tracking.
     */
    fun downloadModel(modelName: String): Flow<DownloadProgress> = flow {
        var partialDownload: File? = null
        try {
            val url = MODEL_SOURCES[modelName]
                ?: throw IllegalArgumentException("Unknown model: $modelName")

            val fileName = when (modelName) {
                "mediapipe_pose" -> "pose_landmarker_lite.task"
                "mediapipe_segmentation" -> "selfie_segmentation.tflite"
                else -> "$modelName.model"
            }

            val outputFile = File(modelsDir, fileName)
            val tempFile = File(modelsDir, "$fileName.part")
            partialDownload = tempFile
            val maxBytes = when (modelName) {
                "mediapipe_pose" -> 16L * 1024 * 1024
                "mediapipe_segmentation" -> 8L * 1024 * 1024
                else -> throw IllegalArgumentException("Unsupported model: $modelName")
            }

            emit(DownloadProgress.Starting)

            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty response")
                val totalBytes = body.contentLength()
                if (totalBytes > maxBytes) throw IllegalStateException("Model download exceeds size limit")

                var downloadedBytes = 0L
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            downloadedBytes += bytesRead
                            if (downloadedBytes > maxBytes) {
                                throw IllegalStateException("Model download exceeds size limit")
                            }
                            output.write(buffer, 0, bytesRead)
                            if (totalBytes > 0) {
                                val progress = (downloadedBytes * 100 / totalBytes).toInt().coerceAtMost(100)
                                emit(DownloadProgress.Progress(progress, downloadedBytes, totalBytes))
                            }
                        }
                        output.flush()
                    }
                }
                if (downloadedBytes == 0L) throw IllegalStateException("Downloaded model is empty")
            }

            if (outputFile.exists() && !outputFile.delete()) {
                throw IllegalStateException("Unable to replace the existing model")
            }
            if (!tempFile.renameTo(outputFile)) {
                throw IllegalStateException("Unable to finalize the model download")
            }

            emit(DownloadProgress.Success(outputFile.absolutePath))

        } catch (e: Exception) {
            partialDownload?.delete()
            emit(DownloadProgress.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Deletes a downloaded model.
     */
    suspend fun deleteModel(fileName: String) = withContext(Dispatchers.IO) {
        File(modelsDir, fileName).delete()
    }

    /**
     * Gets total storage used by models.
     */
    fun getStorageUsed(): Long {
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Ensures required models are available.
     * Returns list of missing model names.
     */
    fun checkRequiredModels(): List<String> {
        val missing = mutableListOf<String>()
        if (!isModelDownloaded("pose_landmarker_lite.task")) {
            missing.add("mediapipe_pose")
        }
        if (!isModelDownloaded("selfie_segmentation.tflite")) {
            missing.add("mediapipe_segmentation")
        }
        return missing
    }

    // ---------------------------------------------------------------- download progress

    sealed class DownloadProgress {
        object Starting : DownloadProgress()
        data class Progress(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress()
        data class Success(val filePath: String) : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }
}
