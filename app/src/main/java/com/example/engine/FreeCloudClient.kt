package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * FREE CLOUD DIFFUSION CLIENT
 *
 * Supports multiple FREE backends for photorealistic virtual try-on:
 *
 * 1. HUGGING FACE INFERENCE API (Free Tier)
 *    - 1,000 requests/month FREE (no credit card needed)
 *    - No API key required for public models
 *    - Rate limit: ~1 req/sec on free tier
 *    - Supports: IDM-VTON, OOTDiffusion, CatVTON
 *    - URL: https://api-inference.huggingface.co/models/{model_id}
 *
 * 2. SELF-HOSTED via NGROK (Completely Free, Uncensored)
 *    - Run diffusion model on your own PC/laptop with GPU
 *    - Expose via ngrok free tunnel
 *    - UNLIMITED requests (limited by your GPU)
 *    - COMPLETELY UNCENSORED - you control the model
 *    - No third party sees your photos
 *
 * 3. GOOGLE COLAB (Free GPU, ~12hrs/day)
 *    - Run on free T4 GPU
 *    - Expose via ngrok or localtunnel
 *    - Good for when your PC doesn't have a GPU
 *
 * 4. KAGGLE (Free GPU, 30hrs/week)
 *    - P100 GPU (faster than Colab T4)
 *    - More reliable than Colab
 *
 * PRIORITY ORDER (auto-selects best available):
 * 1. Self-hosted ngrok (fastest, uncensored, unlimited)
 * 2. Hugging Face Inference API (free, easy, no setup)
 * 3. Google Colab (free GPU, needs setup)
 */
class FreeCloudClient(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // User-configurable endpoints
    private var ngrokEndpoint: String? = null
    private var huggingFaceToken: String? = null
    private var colabEndpoint: String? = null
    private var kaggleEndpoint: String? = null
    private var colabAccessToken: String? = null
    private var kaggleAccessToken: String? = null

    // ---------------------------------------------------------------- configuration

    fun setNgrokEndpoint(url: String) {
        ngrokEndpoint = url.removeSuffix("/")
    }

    fun setHuggingFaceToken(token: String) {
        huggingFaceToken = token
    }

    fun setColabEndpoint(url: String) {
        colabEndpoint = url.removeSuffix("/")
    }

    fun setColabAccessToken(token: String?) {
        colabAccessToken = token
    }

    fun setKaggleEndpoint(url: String) {
        kaggleEndpoint = url.removeSuffix("/")
    }

    fun setKaggleAccessToken(token: String?) {
        kaggleAccessToken = token
    }

    fun clearPairedBackends() {
        colabEndpoint = null
        kaggleEndpoint = null
        colabAccessToken = null
        kaggleAccessToken = null
    }

    /**
     * Returns which backends are currently configured and available.
     */
    fun getAvailableBackends(): Map<String, Boolean> = mapOf(
        "ngrok" to (ngrokEndpoint != null),
        "huggingface" to true,  // Always available (free tier needs no token)
        "colab" to (colabEndpoint != null),
        "kaggle" to (kaggleEndpoint != null)
    )

    // ---------------------------------------------------------------- main generation methods

    /**
     * Try all free backends in priority order until one succeeds.
     * This is the main method the app should call.
     */
    suspend fun generateWithBestBackend(
        personImage: Bitmap,
        garmentImage: Bitmap,
        category: String = "upper_body",
        numSamples: Int = 1
    ): Bitmap {
        val errors = mutableListOf<String>()

        // 1. Try self-hosted ngrok first (best: uncensored, unlimited, fastest)
        if (ngrokEndpoint != null) {
            try {
                return generateWithNgrok(personImage, garmentImage, category, numSamples)
            } catch (e: Exception) {
                errors.add("ngrok: ${e.message}")
            }
        }

        // 2. Try Hugging Face Inference API (free, no setup)
        try {
            return generateWithHuggingFace(personImage, garmentImage, category, numSamples)
        } catch (e: Exception) {
            errors.add("huggingface: ${e.message}")
        }

        // 3. Try Google Colab
        if (colabEndpoint != null) {
            try {
                return generateWithColab(personImage, garmentImage, category, numSamples)
            } catch (e: Exception) {
                errors.add("colab: ${e.message}")
            }
        }

        // 4. Try Kaggle
        if (kaggleEndpoint != null) {
            try {
                return generateWithKaggle(personImage, garmentImage, category, numSamples)
            } catch (e: Exception) {
                errors.add("kaggle: ${e.message}")
            }
        }

        throw IllegalStateException("All free backends failed: ${errors.joinToString("; ")}")
    }

    // ---------------------------------------------------------------- BACKEND 1: Self-Hosted via ngrok

    /**
     * Uses your own PC/laptop running a diffusion model, exposed via ngrok.
     *
     * SETUP INSTRUCTIONS:
     * 1. On your PC with GPU, install dependencies:
     *    pip install torch diffusers transformers accelerate gradio
     *
     * 2. Download a try-on model (e.g., IDM-VTON):
     *    git clone https://github.com/yisol/IDM-VTON.git
     *    cd IDM-VTON
     *
     * 3. Create a simple Gradio API (app.py):
     *    ```python
     *    import gradio as gr
     *    from pipeline import TryOnPipeline
     *
     *    pipe = TryOnPipeline("cuda")
     *
     *    def tryon(person_img, garment_img, category="upper_body"):
     *        result = pipe(person_img, garment_img, category)
     *        return result
     *
     *    demo = gr.Interface(
     *        fn=tryon,
     *        inputs=[gr.Image(), gr.Image(), gr.Text()],
     *        outputs=gr.Image()
     *    )
     *    demo.launch(server_name="0.0.0.0", server_port=7860)
     *    ```
     *
     * 4. Run the app:
     *    python app.py
     *
     * 5. In another terminal, expose via ngrok:
     *    ngrok http 7860
     *
     * 6. Copy the ngrok URL (e.g., https://abc123.ngrok.io)
     *    and paste it in the app settings.
     *
     * BENEFITS:
     * - Completely FREE (no API costs)
     * - UNLIMITED requests
     * - UNCENSORED (you control the model)
     * - PRIVATE (your photos never leave your devices)
     * - BEST QUALITY (full GPU power)
     */
    suspend fun generateWithNgrok(
        personImage: Bitmap,
        garmentImage: Bitmap,
        category: String = "upper_body",
        numSamples: Int = 1
    ): Bitmap = withContext(Dispatchers.IO) {
        val endpoint = ngrokEndpoint
            ?: throw IllegalStateException("ngrok endpoint not configured. Go to Settings > Self-Hosted Backend.")

        val personBase64 = bitmapToBase64(personImage)
        val garmentBase64 = bitmapToBase64(garmentImage)

        val requestBody = JSONObject().apply {
            put("human_img", "data:image/jpeg;base64,$personBase64")
            put("garment_img", "data:image/jpeg;base64,$garmentBase64")
            put("category", category)
            put("num_samples", numSamples)
        }.toString()

        val request = Request.Builder()
            .url("$endpoint/tryon")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseText = response.body?.string()
            ?: throw IllegalStateException("Empty response from ngrok backend")

        if (!response.isSuccessful) {
            throw IllegalStateException("ngrok backend error: ${response.code} - $responseText")
        }

        val json = JSONObject(responseText)

        // Handle both sync and async responses
        if (json.has("image")) {
            // Direct image response (base64)
            val imageBase64 = json.getString("image")
                .replace("data:image/jpeg;base64,", "")
                .replace("data:image/png;base64,", "")
            val imageBytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
            return@withContext BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: throw IllegalStateException("Failed to decode ngrok response image")
        } else if (json.has("output_url")) {
            // URL response
            return@withContext downloadBitmap(json.getString("output_url"))
        } else if (json.has("job_id") || json.has("request_id")) {
            // Async response - poll for result
            val jobId = json.optString("job_id") ?: json.getString("request_id")
            return@withContext pollNgrokResult(endpoint, jobId)
        } else {
            throw IllegalStateException("Unexpected ngrok response format")
        }
    }

    private suspend fun pollNgrokResult(endpoint: String, jobId: String): Bitmap {
        repeat(60) {
            delay(2000)
            val request = Request.Builder()
                .url("$endpoint/status/$jobId")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseText = response.body?.string() ?: return@repeat
            val json = JSONObject(responseText)

            when (json.optString("status")) {
                "completed", "succeeded" -> {
                    val imageUrl = json.getString("output_url")
                    return downloadBitmap(imageUrl)
                }
                "failed" -> throw IllegalStateException(
                    json.optString("error", "Self-hosted generation failed")
                )
            }
        }
        throw IllegalStateException("Self-hosted generation timed out")
    }

    // ---------------------------------------------------------------- BACKEND 2: Hugging Face Inference API (FREE)

    /**
     * Uses Hugging Face's FREE Inference API.
     *
     * NO API KEY NEEDED for public models!
     * You get 1,000 requests/month on the free tier.
     *
     * RECOMMENDED FREE MODELS:
     * - yisol/IDM-VTON       (best overall)
     * - levihsu/OOTDiffusion (most realistic)
     * - zhengchong/CatVTON   (fastest)
     *
     * To get MORE requests (optional):
     * 1. Create a free Hugging Face account
     * 2. Get a token from https://huggingface.co/settings/tokens
     * 3. Paste it in app Settings > Hugging Face Token
     *
     * NO CENSORSHIP on most community models!
     * These are research models, not commercial APIs with content filters.
     */
    suspend fun generateWithHuggingFace(
        personImage: Bitmap,
        garmentImage: Bitmap,
        category: String = "upper_body",
        numSamples: Int = 1
    ): Bitmap = withContext(Dispatchers.IO) {
        val personBase64 = bitmapToBase64(personImage)
        val garmentBase64 = bitmapToBase64(garmentImage)

        // Try multiple models in order of preference
        val models = listOf(
            "yisol/IDM-VTON",
            "levihsu/OOTDiffusion",
            "zhengchong/CatVTON"
        )

        val errors = mutableListOf<String>()

        for (modelId in models) {
            try {
                val requestBody = JSONObject().apply {
                    put("inputs", JSONObject().apply {
                        put("human_img", "data:image/jpeg;base64,$personBase64")
                        put("garment_img", "data:image/jpeg;base64,$garmentBase64")
                        put("category", category)
                        put("num_samples", numSamples)
                    })
                }.toString()

                val requestBuilder = Request.Builder()
                    .url("https://api-inference.huggingface.co/models/$modelId")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))

                // Add token if available (increases rate limits)
                huggingFaceToken?.let { token ->
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()

                if (response.code == 503) {
                    // Model is loading, wait and retry
                    delay(10000)
                    val retryResponse = okHttpClient.newCall(requestBuilder.build()).execute()
                    if (retryResponse.isSuccessful) {
                        return@withContext handleHFResponse(retryResponse)
                    }
                    errors.add("$modelId: Model loading timeout")
                    continue
                }

                if (response.isSuccessful) {
                    return@withContext handleHFResponse(response)
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    errors.add("$modelId: HTTP ${response.code} - $errorBody")
                }
            } catch (e: Exception) {
                errors.add("$modelId: ${e.message}")
            }
        }

        throw IllegalStateException("All HF models failed: ${errors.joinToString("; ")}")
    }

    private fun handleHFResponse(response: okhttp3.Response): Bitmap {
        val bytes = response.body?.bytes()
            ?: throw IllegalStateException("Empty HF response")

        // HF returns the image directly as bytes
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Failed to decode HF response image")
    }

    // ---------------------------------------------------------------- BACKEND 3: Google Colab (Free GPU)

    /**
     * Uses a Google Colab notebook running a diffusion model,
     * exposed via ngrok or localtunnel.
     *
     * SETUP:
     * 1. Open a new Colab notebook
     * 2. Set runtime to GPU (Runtime > Change runtime type > GPU)
     * 3. Install and run a try-on model with Gradio
     * 4. Expose with: !pip install pyngrok && ngrok authtoken YOUR_TOKEN
     * 5. Copy the ngrok URL to app Settings > Colab Endpoint
     *
     * LIMITS:
     * - Free tier: ~12 hours/day of GPU time
     * - Session may disconnect after idle time
     * - Good backup when ngrok PC is not running
     */
    suspend fun generateWithColab(
        personImage: Bitmap,
        garmentImage: Bitmap,
        category: String = "upper_body",
        numSamples: Int = 1
    ): Bitmap = withContext(Dispatchers.IO) {
        val endpoint = colabEndpoint
            ?: throw IllegalStateException("Colab endpoint not configured")
        val accessToken = colabAccessToken
            ?: throw IllegalStateException("Colab pairing token unavailable; reconnect the notebook")

        val personBase64 = bitmapToBase64(personImage)
        val garmentBase64 = bitmapToBase64(garmentImage)

        val requestBody = JSONObject().apply {
            put("human_img", "data:image/jpeg;base64,$personBase64")
            put("garment_img", "data:image/jpeg;base64,$garmentBase64")
            put("category", category)
            put("num_samples", numSamples)
        }.toString()

        val request = Request.Builder()
            .url("$endpoint/tryon")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string()
                ?: throw IllegalStateException("Empty response from Colab")
            if (!response.isSuccessful) {
                throw IllegalStateException("Colab request failed (${response.code})")
            }

            val json = JSONObject(responseText)
            val imageData = json.optString("image")
            if (imageData.startsWith("data:image/")) {
                val encoded = imageData.substringAfter(',', "")
                val imageBytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                return@withContext BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: throw IllegalStateException("Colab returned an invalid image")
            }

            // Compatibility with older notebook versions: accept only a URL on
            // the already-paired tunnel host, never an arbitrary download URL.
            val imageUrl = json.optString("output_url")
            val parsedImageUrl = imageUrl.toHttpUrlOrNull()
            val parsedEndpoint = endpoint.toHttpUrlOrNull()
            if (parsedImageUrl == null || parsedEndpoint == null ||
                parsedImageUrl.scheme != "https" || parsedImageUrl.host != parsedEndpoint.host
            ) {
                throw IllegalStateException("Colab returned an unsupported image response")
            }
            downloadBitmap(imageUrl)
        }
    }

    // ---------------------------------------------------------------- BACKEND 4: Kaggle (Free GPU)

    /**
     * Uses Kaggle notebooks with free P100 GPU.
     * Similar setup to Colab but more reliable.
     *
     * LIMITS:
     * - 30 hours/week of GPU time
     * - P100 is faster than Colab's T4
     */
    suspend fun generateWithKaggle(
        personImage: Bitmap,
        garmentImage: Bitmap,
        category: String = "upper_body",
        numSamples: Int = 1
    ): Bitmap = withContext(Dispatchers.IO) {
        val endpoint = kaggleEndpoint
            ?: throw IllegalStateException("Kaggle endpoint not configured")
        val accessToken = kaggleAccessToken
            ?: throw IllegalStateException("Kaggle pairing token unavailable; reconnect the notebook")

        val personBase64 = bitmapToBase64(personImage)
        val garmentBase64 = bitmapToBase64(garmentImage)

        val requestBody = JSONObject().apply {
            put("human_img", "data:image/jpeg;base64,$personBase64")
            put("garment_img", "data:image/jpeg;base64,$garmentBase64")
            put("category", category)
            put("num_samples", numSamples)
        }.toString()

        val request = Request.Builder()
            .url("$endpoint/tryon")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string()
                ?: throw IllegalStateException("Empty response from Kaggle")
            if (!response.isSuccessful) {
                throw IllegalStateException("Kaggle request failed (${response.code})")
            }

            val json = JSONObject(responseText)
            val imageData = json.optString("image")
            if (imageData.startsWith("data:image/")) {
                val encoded = imageData.substringAfter(',', "")
                val imageBytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                return@withContext BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: throw IllegalStateException("Kaggle returned an invalid image")
            }

            val imageUrl = json.optString("output_url")
            val parsedImageUrl = imageUrl.toHttpUrlOrNull()
            val parsedEndpoint = endpoint.toHttpUrlOrNull()
            if (parsedImageUrl == null || parsedEndpoint == null ||
                parsedImageUrl.scheme != "https" || parsedImageUrl.host != parsedEndpoint.host
            ) {
                throw IllegalStateException("Kaggle returned an unsupported image response")
            }
            downloadBitmap(imageUrl)
        }
    }

    // ---------------------------------------------------------------- utilities

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        return if (android.os.Build.VERSION.SDK_INT >= 26) {
            java.util.Base64.getEncoder().encodeToString(output.toByteArray())
        } else {
            android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
        }
    }

    private fun downloadBitmap(url: String): Bitmap {
        val request = Request.Builder().url(url).build()
        return okHttpClient.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes()
                ?: throw IllegalStateException("Empty image download")
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Failed to decode downloaded image")
        }
    }
}
