package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * IMAGE ENHANCER - Pure Android (no OpenCV)
 *
 * Post-processing for photorealistic try-on results.
 * Uses Android Canvas, RenderScript (or fallback), and ColorMatrix.
 */
class ImageEnhancer(private val context: Context) {

    companion object {
        private const val TAG = "ImageEnhancer"
    }

    /**
     * Enhances a try-on result for photorealistic appearance.
     */
    suspend fun enhanceTryOnResult(
        generated: Bitmap,
        original: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        var result = generated.copy(Bitmap.Config.ARGB_8888, true)!!

        try {
            // Step 1: Color matching to original
            result = matchColorTemperature(result, original)

            // Step 2: Skin tone preservation
            result = preserveSkinTone(result, original)

            // Step 3: Edge feathering
            result = featherEdges(result, original)

            // Step 4: Subtle sharpening
            result = sharpen(result)

            // Step 5: Final color grading
            result = colorGrade(result)

        } catch (e: Exception) {
            Log.w(TAG, "Enhancement step failed: ${e.message}")
        }

        result
    }

    // ---------------------------------------------------------------- color matching

    private fun matchColorTemperature(generated: Bitmap, original: Bitmap): Bitmap {
        val genAvg = getAverageColor(generated)
        val origAvg = getAverageColor(original)

        val dr = origAvg[0] - genAvg[0]
        val dg = origAvg[1] - genAvg[1]
        val db = origAvg[2] - genAvg[2]

        val matrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    1f, 0f, 0f, 0f, dr,
                    0f, 1f, 0f, 0f, dg,
                    0f, 0f, 1f, 0f, db,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }

        return applyColorMatrix(generated, matrix)
    }

    private fun getAverageColor(bitmap: Bitmap): FloatArray {
        var r = 0L
        var g = 0L
        var b = 0L
        val width = bitmap.width
        val height = bitmap.height
        var count = 0

        // Sample every 10th pixel for performance
        for (y in 0 until height step 10) {
            for (x in 0 until width step 10) {
                val pixel = bitmap.getPixel(x, y)
                r += android.graphics.Color.red(pixel)
                g += android.graphics.Color.green(pixel)
                b += android.graphics.Color.blue(pixel)
                count++
            }
        }

        return floatArrayOf(
            r / count.toFloat() - 128f,
            g / count.toFloat() - 128f,
            b / count.toFloat() - 128f
        )
    }

    // ---------------------------------------------------------------- skin tone preservation

    private fun preserveSkinTone(result: Bitmap, original: Bitmap): Bitmap {
        val output = result.copy(Bitmap.Config.ARGB_8888, true)!!
        val canvas = Canvas(output)

        val skinMask = detectSkinRegion(original)
        val paint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            alpha = 180
        }

        canvas.drawBitmap(skinMask, 0f, 0f, paint)
        skinMask.recycle()

        return output
    }

    private fun detectSkinRegion(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)

            // Simple skin detection heuristic
            val isSkin = r > 95 && g > 40 && b > 20 &&
                    (maxOf(r, g, b) - minOf(r, g, b)) > 15 &&
                    r > g && r > b &&
                    kotlin.math.abs(r - g) > 15

            pixels[i] = if (isSkin) 0xFF else 0x00
        }

        mask.setPixels(pixels, 0, width, 0, 0, width, height)
        return mask
    }

    // ---------------------------------------------------------------- edge feathering

    private fun featherEdges(result: Bitmap, original: Bitmap): Bitmap {
        val width = result.width
        val height = result.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Draw original as base
        canvas.drawBitmap(original, 0f, 0f, null)

        // Create feathered overlay
        val overlayPaint = Paint().apply {
            isFilterBitmap = true
            isDither = true
            alpha = 245
        }

        // Apply radial gradient for soft edges
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.45f

        val gradient = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(
                android.graphics.Color.WHITE,
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        val gradientPaint = Paint().apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }

        val tempBitmap = result.copy(Bitmap.Config.ARGB_8888, true)!!
        val tempCanvas = Canvas(tempBitmap)
        tempCanvas.drawPaint(gradientPaint)

        canvas.drawBitmap(tempBitmap, 0f, 0f, overlayPaint)
        tempBitmap.recycle()

        return output
    }

    // ---------------------------------------------------------------- sharpening

    private fun sharpen(bitmap: Bitmap): Bitmap {
        // Simple unsharp mask using convolution approximation
        val blurred = fastBlur(bitmap, 1.2f)
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw original
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Blend with inverted blurred (unsharp mask)
        val paint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            alpha = 40
        }
        canvas.drawBitmap(blurred, 0f, 0f, paint)
        blurred.recycle()

        return result
    }

    private fun fastBlur(bitmap: Bitmap, radius: Float): Bitmap {
        return try {
            val rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, bitmap)
            val output = Allocation.createTyped(rs, input.type)
            val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(radius)
            blur.setInput(input)
            blur.forEach(output)
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            output.copyTo(result)
            rs.destroy()
            result
        } catch (e: Exception) {
            // Fallback: return copy
            bitmap.copy(Bitmap.Config.ARGB_8888, true)!!
        }
    }

    // ---------------------------------------------------------------- color grading

    private fun colorGrade(bitmap: Bitmap): Bitmap {
        // Slight contrast and saturation boost
        val matrix = ColorMatrix().apply {
            // Saturation +15%
            postConcat(ColorMatrix().apply { setSaturation(1.15f) })
            // Contrast +10%
            val scale = 1.1f
            val translate = (-0.1f * 128f + 0.5f)
            postConcat(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            })
        }

        return applyColorMatrix(bitmap, matrix)
    }

    // ---------------------------------------------------------------- utilities

    private fun applyColorMatrix(bitmap: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun cleanup() {
        // Nothing to clean up for pure Android implementation
    }
}
