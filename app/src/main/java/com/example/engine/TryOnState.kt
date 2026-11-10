package com.example.engine

import android.graphics.Bitmap

/**
 * Sealed interface representing all states of the try-on pipeline.
 * Emitted as Flow<TryOnState> for real-time UI updates.
 */
sealed interface TryOnState {
    object Idle : TryOnState

    data class Preprocessing(
        val step: String = "Loading images",
        val progress: Float = 0f
    ) : TryOnState

    data class DetectingBody(
        val step: String = "Detecting body pose",
        val progress: Float = 0f
    ) : TryOnState

    data class Segmenting(
        val step: String = "Segmenting person",
        val progress: Float = 0f
    ) : TryOnState

    data class GarmentWarping(
        val step: String = "Warping garment to body",
        val progress: Float = 0f
    ) : TryOnState

    data class CloudGenerating(
        val step: String = "Generating with AI (cloud)",
        val progress: Float = 0f,
        val backendName: String = ""
    ) : TryOnState

    data class EnhancingImage(
        val step: String = "Enhancing result",
        val progress: Float = 0f
    ) : TryOnState

    data class SavingResult(
        val step: String = "Saving result",
        val progress: Float = 0.95f
    ) : TryOnState

    data class Success(
        val resultBitmap: Bitmap,
        val resultUri: String,
        val processingTimeMs: Long,
        val backendUsed: String = "local"
    ) : TryOnState

    data class Error(
        val message: String,
        val recoverable: Boolean = true,
        val fallbackOptions: List<String> = emptyList()
    ) : TryOnState

    data class Cancelled(
        val reason: String = "User cancelled"
    ) : TryOnState
}
