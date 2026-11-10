package com.example.engine.autocolab

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * SELF-HOSTED BACKEND MANAGER
 *
 * Manages connections to the user's own PC/laptop running a diffusion model.
 * This is the BEST option for uncensored, unlimited, fastest processing.
 *
 * HOW IT WORKS:
 * 1. User runs a diffusion model on their PC (with GPU)
 * 2. Exposes it via ngrok or localtunnel
 * 3. Pastes the ngrok URL in app settings (or auto-detects via pairing)
 * 4. App sends photos to the PC, gets results back
 *
 * AUTO-DETECTION:
 * - Checks if a previously configured endpoint is still running
 * - Attempts to reconnect automatically
 * - Falls back to manual configuration if needed
 *
 * SETUP ON PC:
 * 1. Install Python, PyTorch, Diffusers
 * 2. Download a try-on model (IDM-VTON, OOTDiffusion, etc.)
 * 3. Run the Gradio/Flask server
 * 4. Expose via ngrok: ngrok http 7860
 * 5. Copy the URL to the app (or use auto-pairing)
 */
class SelfHostedManager(private val context: Context) {

    companion object {
        private const val TAG = "SelfHostedManager"
        private const val PREFS_NAME = "selfhosted_prefs"
        private const val KEY_ENDPOINT = "endpoint_url"
        private const val KEY_LAST_CONNECTED = "last_connected"
        private const val KEY_GPU_NAME = "gpu_name"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // ---------------------------------------------------------------- public API

    /**
     * Detects if a previously configured self-hosted server is still running.
     * Returns the endpoint URL if active, null otherwise.
     */
    suspend fun detectRunningServer(): String? = withContext(Dispatchers.IO) {
        val savedEndpoint = prefs.getString(KEY_ENDPOINT, null)
            ?: return@withContext null

        if (isServerActive(savedEndpoint)) {
            Log.d(TAG, "Self-hosted server detected: $savedEndpoint")
            savedEndpoint
        } else {
            Log.d(TAG, "Saved endpoint not responding: $savedEndpoint")
            null
        }
    }

    /**
     * Saves a self-hosted endpoint URL.
     */
    fun saveEndpoint(url: String) {
        prefs.edit().apply {
            putString(KEY_ENDPOINT, url.removeSuffix("/"))
            putLong(KEY_LAST_CONNECTED, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Gets the saved endpoint URL.
     */
    fun getSavedEndpoint(): String? = prefs.getString(KEY_ENDPOINT, null)

    /**
     * Clears the saved endpoint.
     */
    fun clearEndpoint() {
        prefs.edit().remove(KEY_ENDPOINT).apply()
    }

    /**
     * Tests if a given endpoint is responding.
     */
    suspend fun testEndpoint(url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = url.removeSuffix("/")
            val request = Request.Builder()
                .url("$normalizedUrl/health")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseText = response.body?.string()

            if (response.isSuccessful && responseText != null) {
                val json = org.json.JSONObject(responseText)
                val gpu = json.optString("gpu", "Unknown GPU")
                val status = json.optString("status", "unknown")

                if (status == "ok" || status == "running") {
                    saveEndpoint(normalizedUrl)
                    saveGpuName(gpu)
                    return@withContext Pair(true, gpu)
                }
            }

            Pair(false, "Server responded but not ready: HTTP ${response.code}")

        } catch (e: Exception) {
            Log.w(TAG, "Endpoint test failed: ${e.message}")
            Pair(false, e.message ?: "Connection failed")
        }
    }

    /**
     * Returns info about the last successful connection.
     */
    fun getLastConnectionInfo(): SelfHostedInfo? {
        val endpoint = prefs.getString(KEY_ENDPOINT, null) ?: return null
        val lastConnected = prefs.getLong(KEY_LAST_CONNECTED, 0)
        val gpuName = prefs.getString(KEY_GPU_NAME, "Unknown") ?: "Unknown"

        return if (lastConnected > 0) {
            SelfHostedInfo(endpoint, lastConnected, gpuName)
        } else null
    }

    // ---------------------------------------------------------------- private

    private fun isServerActive(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$url/health")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val isActive = response.isSuccessful
            response.close()
            isActive
        } catch (e: Exception) {
            false
        }
    }

    private fun saveGpuName(gpu: String) {
        prefs.edit().putString(KEY_GPU_NAME, gpu).apply()
    }

    data class SelfHostedInfo(
        val endpoint: String,
        val lastConnectedTimestamp: Long,
        val gpuName: String
    ) {
        fun getTimeAgoText(): String {
            val diff = System.currentTimeMillis() - lastConnectedTimestamp
            val minutes = diff / (1000 * 60)
            val hours = minutes / 60
            val days = hours / 24

            return when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "$minutes min ago"
                hours < 24 -> "$hours hr ago"
                days < 7 -> "$days days ago"
                else -> "Long ago"
            }
        }
    }
}
