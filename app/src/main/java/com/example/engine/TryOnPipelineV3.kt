package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.Log
import com.example.data.GarmentCategory
import com.example.data.TryOnHistoryEntity
import com.example.engine.autocolab.HuggingFaceManager
import com.example.engine.autocolab.UnifiedGpuManager
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * PRODUCTION-GRADE TRY-ON PIPELINE V3
 *
 * Uses SelfieMulticlass (category 4 = clothes) for precise garment replacement.
 * Emits TryOnState via Flow for real-time UI progress tracking.
 *
 * 3-Tier architecture with automatic fallback:
 *   Tier 1: Ultra-Light (heuristic body region + Canvas overlay)
 *   Tier 2: On-Device AI (Pose landmarks + SelfieMulticlass clothes segmentation)
 *   Tier 3: Free Cloud GPU (HuggingFace / Colab / Kaggle via UnifiedGpuManager)
 *
 * All processing runs on Dispatchers.IO via flowOn().
 */
class TryOnPipelineV3(
    private val context: Context,
    private val unifiedGpuManager: UnifiedGpuManager,
    private val freeCloudClient: FreeCloudClient
) {

    companion object {
        private const val TAG = "TryOnPipelineV3"

        // SelfieMulticlass category indices
        private const val CATEGORY_BACKGROUND = 0
        private const val CATEGORY_HAIR = 1
        private const val CATEGORY_BODY_SKIN = 2
        private const val CATEGORY_FACE_SKIN = 3
        private const val CATEGORY_CLOTHES = 4
        private const val CATEGORY_OTHERS = 5
    }

    private val deviceProfiler = DeviceProfiler(context)
    private val imageEnhancer = ImageEnhancer(context)
    private val hfManager = HuggingFaceManager(context)

    private var poseLandmarker: PoseLandmarker? = null
    private var imageSegmenter: ImageSegmenter? = null

    init {
        initializeMediaPipe()
    }

    private fun initializeMediaPipe() {
        // Pose Landmarker (lite) - for body landmark detection
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()
            val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, poseOptions)
            Log.d(TAG, "PoseLandmarker (lite) initialized")
        } catch (e: Exception) {
            Log.w(TAG, "Pose model unavailable: ${e.message}")
        }

        // SelfieMulticlass segmenter - for clothes region isolation
        try {
            val segBaseOptions = BaseOptions.builder()
                .setModelAssetPath("selfie_multiclass_256.tflite")
                .build()
            val segOptions = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(segBaseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputType(ImageSegmenter.ImageSegmenterOptions.OutputType.CATEGORY_MASK)
                .build()
            imageSegmenter = ImageSegmenter.createFromOptions(context, segOptions)
            Log.d(TAG, "SelfieMulticlass segmenter initialized")
        } catch (e: Exception) {
            Log.w(TAG, "SelfieMulticlass segmenter unavailable: ${e.message}")
            imageSegmenter = null
        }
    }

    /**
     * Main entry point. Executes try-on with real-time state emission.
     *
     * @param personImage The user's photo
     * @param garmentImage The garment to try on
     * @param category Garment category for body region detection
     * @param preferCloud If true, attempts cloud generation first
     */
    fun executeTryOn(
        personImage: Bitmap,
        garmentImage: Bitmap,
        category: GarmentCategory = GarmentCategory.UPPER,
        preferCloud: Boolean = false
    ): Flow<TryOnState> = flow {
        val startTime = System.currentTimeMillis()

        if (!currentCoroutineContext().isActive) {
            emit(TryOnState.Cancelled())
            return@flow
        }

        emit(TryOnState.Preprocessing("Loading and validating images", 0.05f))

        try {
            // Validate inputs
            if (personImage.width < 64 || personImage.height < 64) {
                emit(TryOnState.Error("Person image too small. Minimum 64x64 required.", false))
                return@flow
            }
            if (garmentImage.width < 64 || garmentImage.height < 64) {
                emit(TryOnState.Error("Garment image too small. Minimum 64x64 required.", false))
                return@flow
            }

            emit(TryOnState.Preprocessing("Analyzing device capability", 0.15f))
            val mode = deviceProfiler.recommendProcessingMode()
            val capability = deviceProfiler.getDeviceCapability()
            Log.d(TAG, "Device: $capability, Mode: $mode")

            emit(TryOnState.Preprocessing("Preparing images", 0.25f))

            val result = when {
                preferCloud || mode == DeviceProfiler.ProcessingMode.FREE_CLOUD -> {
                    executeCloudTier(personImage, garmentImage, category, emit)
                }
                mode == DeviceProfiler.ProcessingMode.ON_DEVICE_AI -> {
                    executeOnDeviceTier(personImage, garmentImage, category, emit)
                }
                else -> {
                    executeUltraLightTier(personImage, garmentImage, category, emit)
                }
            }

            if (result != null) {
                emit(TryOnState.SavingResult("Saving result", 0.95f))
                val uri = saveBitmapToCache(result)
                val elapsed = System.currentTimeMillis() - startTime
                emit(TryOnState.Success(result, uri, elapsed, if (preferCloud) "huggingface" else "local"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error", e)
            emit(TryOnState.Error(
                message = e.message ?: "Unknown pipeline error",
                recoverable = true,
                fallbackOptions = listOf("huggingface", "ultra_light")
            ))
        }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------- tier 1: ultra-light

    private suspend fun executeUltraLightTier(
        personImage: Bitmap,
        garmentImage: Bitmap,
        category: GarmentCategory,
        emit: suspend (TryOnState) -> Unit
    ): Bitmap? {
        emit(TryOnState.DetectingBody("Detecting body region", 0.3f))
        val bodyRect = detectBodyRegionSafe(personImage, category)

        emit(TryOnState.GarmentWarping("Positioning garment", 0.5f))
        val result = personImage.copy(Bitmap.Config.ARGB_8888, true)!!
        val canvas = Canvas(result)

        val scaledGarment = Bitmap.createScaledBitmap(
            garmentImage, bodyRect.width(), bodyRect.height(), true
        )

        val paint = Paint().apply {
            alpha = 220
            isFilterBitmap = true
            isDither = true
        }

        canvas.drawBitmap(scaledGarment, bodyRect.left.toFloat(), bodyRect.top.toFloat(), paint)
        scaledGarment.recycle()

        emit(TryOnState.EnhancingImage("Applying enhancements", 0.8f))
        return imageEnhancer.enhanceTryOnResult(result, personImage)
    }

    // ---------------------------------------------------------------- tier 2: on-device AI

    private suspend fun executeOnDeviceTier(
        personImage: Bitmap,
        garmentImage: Bitmap,
        category: GarmentCategory,
        emit: suspend (TryOnState) -> Unit
    ): Bitmap? {
        val width = personImage.width
        val height = personImage.height

        emit(TryOnState.Segmenting("Segmenting clothes region", 0.35f))
        val mask = getSegmentationMask(personImage)

        emit(TryOnState.DetectingBody("Detecting body landmarks", 0.45f))
        val bodyRect = detectBodyRegionSafe(personImage, category)

        emit(TryOnState.GarmentWarping("Replacing clothes with new garment", 0.6f))
        val scaledGarment = Bitmap.createScaledBitmap(
            garmentImage, bodyRect.width(), bodyRect.height(), true
        )

        // Use SelfieMulticlass clothes mask (category 4) if available
        val clothesMaskBitmap = if (mask != null && imageSegmenter != null) {
            createClothesMaskBitmap(mask, width, height)
        } else {
            // Fallback: heuristic rectangle mask
            Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply {
                val c = Canvas(this)
                c.drawRect(bodyRect, Paint().apply { alpha = 255 })
            }
        }

        val blended = blendWithClothesMask(personImage, scaledGarment, clothesMaskBitmap, bodyRect)
        scaledGarment.recycle()
        clothesMaskBitmap.recycle()

        emit(TryOnState.EnhancingImage("Enhancing photorealism", 0.85f))
        return imageEnhancer.enhanceTryOnResult(blended, personImage)
    }

    // ---------------------------------------------------------------- tier 3: free cloud

    private suspend fun executeCloudTier(
        personImage: Bitmap,
        garmentImage: Bitmap,
        category: GarmentCategory,
        emit: suspend (TryOnState) -> Unit
    ): Bitmap? {
        val connected = unifiedGpuManager.state.value as? UnifiedGpuManager.UnifiedState.Connected
        val backendName = connected?.backend?.id ?: "huggingface"
        emit(TryOnState.CloudGenerating("Connecting to free AI backend", 0.3f, backendName))

        val categoryStr = when (category) {
            GarmentCategory.DRESS -> "dresses"
            GarmentCategory.LOWER -> "lower_body"
            else -> "upper_body"
        }

        return try {
            emit(TryOnState.CloudGenerating("Generating photorealistic try-on", 0.5f, backendName))
            val generated = when (backendName) {
                "colab" -> freeCloudClient.generateWithColab(personImage, garmentImage, categoryStr)
                "kaggle" -> freeCloudClient.generateWithKaggle(personImage, garmentImage, categoryStr)
                else -> hfManager.generateTryOn(personImage, garmentImage, categoryStr)
            }

            emit(TryOnState.EnhancingImage("Post-processing result", 0.85f))
            imageEnhancer.enhanceTryOnResult(generated, personImage)
        } catch (e: Exception) {
            Log.w(TAG, "Cloud generation failed, falling back to on-device", e)
            emit(TryOnState.Preprocessing("Cloud failed, switching to on-device", 0.25f))
            executeOnDeviceTier(personImage, garmentImage, category, emit)
        }
    }

    // ---------------------------------------------------------------- body detection

    /**
     * Detects body region using pose landmarks when available,
     * falls back to heuristic rectangle detection.
     */
    private fun detectBodyRegionSafe(bitmap: Bitmap, category: GarmentCategory): Rect {
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = poseLandmarker?.detect(mpImage)
            val landmarks = result?.poseLandmarks()?.firstOrNull()
            if (landmarks != null && landmarks.size >= 33) {
                detectBodyRegionFromLandmarks(bitmap, landmarks, category)
            } else {
                detectBodyRegionHeuristic(bitmap, category)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pose detection failed, using heuristic fallback")
            detectBodyRegionHeuristic(bitmap, category)
        }
    }

    /**
     * Landmark-aware body region detection using Pose Landmarker.
     * Key landmarks: 11=left shoulder, 12=right shoulder, 23=left hip, 24=right hip
     */
    private fun detectBodyRegionFromLandmarks(
        bitmap: Bitmap,
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        category: GarmentCategory
    ): Rect {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]

        return when (category) {
            GarmentCategory.UPPER -> {
                val left = (min(leftShoulder.x(), rightShoulder.x()) * w * 0.85f).toInt()
                    .coerceAtLeast(0)
                val top = (min(leftShoulder.y(), rightShoulder.y()) * h * 0.9f).toInt()
                    .coerceAtLeast(0)
                val right = (max(leftShoulder.x(), rightShoulder.x()) * w * 1.15f).toInt()
                    .coerceAtMost(bitmap.width)
                val bottom = (((leftHip.y() + rightHip.y()) / 2f) * h * 1.05f).toInt()
                    .coerceAtMost(bitmap.height)
                Rect(left, top, right, bottom)
            }
            GarmentCategory.LOWER -> {
                val left = (min(leftHip.x(), rightHip.x()) * w * 0.9f).toInt()
                    .coerceAtLeast(0)
                val top = (((leftHip.y() + rightHip.y()) / 2f) * h * 0.95f).toInt()
                    .coerceAtLeast(0)
                val right = (max(leftHip.x(), rightHip.x()) * w * 1.1f).toInt()
                    .coerceAtMost(bitmap.width)
                val bottom = (h * 0.95f).toInt().coerceAtMost(bitmap.height)
                Rect(left, top, right, bottom)
            }
            GarmentCategory.DRESS -> {
                val left = (min(leftShoulder.x(), rightShoulder.x()) * w * 0.85f).toInt()
                    .coerceAtLeast(0)
                val top = (min(leftShoulder.y(), rightShoulder.y()) * h * 0.85f).toInt()
                    .coerceAtLeast(0)
                val right = (max(leftShoulder.x(), rightShoulder.x()) * w * 1.15f).toInt()
                    .coerceAtMost(bitmap.width)
                val bottom = (h * 0.95f).toInt().coerceAtMost(bitmap.height)
                Rect(left, top, right, bottom)
            }
            else -> detectBodyRegionHeuristic(bitmap, category)
        }
    }

    /**
     * Heuristic body region detection when pose landmarks are unavailable.
     */
    private fun detectBodyRegionHeuristic(bitmap: Bitmap, category: GarmentCategory): Rect {
        val w = bitmap.width
        val h = bitmap.height
        return when (category) {
            GarmentCategory.UPPER -> Rect(
                (w * 0.15).toInt(), (h * 0.15).toInt(),
                (w * 0.85).toInt(), (h * 0.55).toInt()
            )
            GarmentCategory.LOWER -> Rect(
                (w * 0.2).toInt(), (h * 0.45).toInt(),
                (w * 0.8).toInt(), (h * 0.9).toInt()
            )
            GarmentCategory.DRESS -> Rect(
                (w * 0.15).toInt(), (h * 0.12).toInt(),
                (w * 0.85).toInt(), (h * 0.88).toInt()
            )
            else -> Rect(
                (w * 0.15).toInt(), (h * 0.15).toInt(),
                (w * 0.85).toInt(), (h * 0.7).toInt()
            )
        }
    }

    // ---------------------------------------------------------------- segmentation helpers

    private fun getSegmentationMask(bitmap: Bitmap): ByteArray? {
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = imageSegmenter?.segment(mpImage)
            val mask = result?.categoryMask()?.get()
            result?.close()
            mask
        } catch (e: Exception) {
            Log.w(TAG, "Segmentation failed: ${e.message}")
            null
        }
    }

    /**
     * Creates a mask where ONLY the clothes region (category 4 from SelfieMulticlass)
     * is opaque (255). All other categories (background, hair, skin, face, accessories)
     * are transparent (0).
     *
     * SelfieMulticlass categories:
     *   0 = background
     *   1 = hair
     *   2 = body-skin
     *   3 = face-skin
     *   4 = clothes        <-- we target this
     *   5 = others (accessories)
     */
    private fun createClothesMaskBitmap(maskBytes: ByteArray, width: Int, height: Int): Bitmap {
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val pixels = IntArray(width * height)
        for (i in maskBytes.indices) {
            val category = maskBytes[i].toInt()
            val value = if (category == CATEGORY_CLOTHES) 255 else 0
            pixels[i] = value
        }
        maskBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return maskBitmap
    }

    // ---------------------------------------------------------------- blending

    /**
     * Blends the new garment into the person image using the clothes mask.
     * Only pixels where the mask is opaque (clothes region) get replaced.
     * Skin, hair, face, and background remain untouched.
     */
    private fun blendWithClothesMask(
        original: Bitmap,
        garment: Bitmap,
        clothesMask: Bitmap,
        position: Rect
    ): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)!!
        val canvas = Canvas(result)

        // Create a full-size garment layer positioned at the body rect
        val garmentLayer = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val garmentCanvas = Canvas(garmentLayer)
        garmentCanvas.drawBitmap(garment, position.left.toFloat(), position.top.toFloat(), null)

        // Apply the clothes mask to the garment layer using DST_IN
        // This makes only the clothes-region pixels visible
        val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        garmentCanvas.drawBitmap(clothesMask, 0f, 0f, maskPaint)

        // Blend the masked garment onto the original with slight transparency
        val blendPaint = Paint().apply {
            alpha = 240
            isFilterBitmap = true
            isDither = true
        }
        canvas.drawBitmap(garmentLayer, 0f, 0f, blendPaint)
        garmentLayer.recycle()

        return result
    }

    // ---------------------------------------------------------------- persistence

    private fun saveBitmapToCache(bitmap: Bitmap): String {
        val file = File(context.cacheDir, "tryon_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file.absolutePath
    }

    fun getGpuManager(): UnifiedGpuManager = unifiedGpuManager

    fun cleanup() {
        poseLandmarker?.close()
        imageSegmenter?.close()
    }
}
