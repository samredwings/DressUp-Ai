package com.example.engine.autocolab

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AUTO-KAGGLE MANAGER
 *
 * Handles automatic connection to Kaggle's free GPU notebooks.
 *
 * KAGGLE FREE TIER:
 * - GPU: NVIDIA P100 (faster than Colab's T4)
 * - Hours: 30 hours per week
 * - Reliability: Better than Colab (sessions last longer)
 * - Setup: Similar to Colab but more stable
 *
 * HOW IT WORKS:
 * 1. App generates pairing code
 * 2. Opens Kaggle notebook URL with pairing code embedded
 * 3. User clicks "Run All" in Kaggle notebook
 * 4. Notebook starts server, creates tunnel, auto-registers
 * 5. App polls pairing server, auto-connects
 *
 * SETUP FOR USERS:
 * 1. Create free Kaggle account at https://kaggle.com
 * 2. Enable GPU in notebook settings
 * 3. Run the notebook (one button)
 * 4. App connects automatically
 */
class AutoKaggleManager(private val context: Context) {

    companion object {
        private const val TAG = "AutoKaggleManager"

        // Kaggle notebook URL (hosted on GitHub, opened via Kaggle)
        private const val KAGGLE_NOTEBOOK_URL =
            "https://www.kaggle.com/code/samredwings/dressup-tryon-backend"

        // Pairing server
        private const val PAIRING_SERVER = "https://dressup-pairing.deno.dev"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ---------------------------------------------------------------- state

    sealed class KaggleState {
        object Idle : KaggleState()
        object GeneratingCode : KaggleState()
        data class AwaitingKaggle(
            val pairingCode: String,
            val kaggleUrl: String,
            val timeElapsedSeconds: Int
        ) : KaggleState()
        object PollingForUrl : KaggleState()
        data class Connected(
            val endpointUrl: String,
            val gpuName: String,
            val accessToken: String? = null
        ) : KaggleState()
        data class Failed(val reason: String) : KaggleState()
        object Cancelled : KaggleState()
    }

    private val _state = MutableStateFlow<KaggleState>(KaggleState.Idle)
    val state: StateFlow<KaggleState> = _state

    private var pollingJob: Job? = null

    // ---------------------------------------------------------------- public API

    /**
     * Starts the auto-Kaggle connection flow.
     */
    fun startConnectionFlow() {
        cancelConnection()

        scope.launch {
            try {
                _state.value = KaggleState.GeneratingCode

                val pairingCode = PairingSecurity.newToken()
                val kaggleUrl = buildKaggleUrl(pairingCode)

                // Open Kaggle in browser
                withContext(Dispatchers.Main) {
                    openKaggleInBrowser(kaggleUrl)
                }

                _state.value = KaggleState.AwaitingKaggle(
                    pairingCode = pairingCode,
                    kaggleUrl = kaggleUrl,
                    timeElapsedSeconds = 0
                )

                startPolling(pairingCode)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Kaggle flow", e)
                _state.value = KaggleState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun cancelConnection() {
        pollingJob?.cancel()
        pollingJob = null
        _state.value = KaggleState.Cancelled
    }

    fun reset() {
        pollingJob?.cancel()
        pollingJob = null
        _state.value = KaggleState.Idle
    }

    /**
     * Checks if a Kaggle endpoint is currently active.
     */
    suspend fun checkStatus(): Triple<Boolean, Boolean, String?> {
        // Kaggle requires the notebook to be running
        // We can check the pairing server for an active Kaggle registration
        return Triple(true, false, null) // Available but not active until connected
    }

    fun cleanup() {
        pollingJob?.cancel()
        scope.cancel()
    }

    // ---------------------------------------------------------------- private

    private fun buildKaggleUrl(pairingCode: String): String {
        val params = JSONObject().apply {
            put("pairing_code", pairingCode)
            put("pairing_server", PAIRING_SERVER)
        }.toString()

        val encodedParams = android.util.Base64.encodeToString(
            params.toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )

        return "$KAGGLE_NOTEBOOK_URL?config=$encodedParams"
    }

    private fun openKaggleInBrowser(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun startPolling(pairingCode: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var elapsedSeconds = 0
            val maxWaitSeconds = 600  // 10 minutes
            val pollIntervalSeconds = 3

            while (isActive && elapsedSeconds < maxWaitSeconds) {
                try {
                    val statusUrl = "$PAIRING_SERVER/status"
                    val request = Request.Builder()
                        .url(statusUrl)
                        .header("Authorization", "Bearer $pairingCode")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseText = response.use { it.body?.string() }

                    if (response.isSuccessful && responseText != null) {
                        val json = JSONObject(responseText)
                        val status = json.optString("status", "waiting")

                        when (status) {
                            "ready" -> {
                                val endpointUrl = PairingSecurity.validateTunnelUrl(
                                    json.getString("endpoint_url")
                                ) ?: throw IllegalStateException("Pairing server returned an untrusted HTTPS endpoint")
                                val gpuName = json.optString("gpu", "NVIDIA P100")
                                _state.value = KaggleState.Connected(endpointUrl, gpuName, pairingCode)
                                return@launch
                            }
                            "error" -> {
                                val errorMsg = json.optString("message", "Kaggle reported an error")
                                _state.value = KaggleState.Failed(errorMsg)
                                return@launch
                            }
                            else -> {
                                _state.value = KaggleState.AwaitingKaggle(
                                    pairingCode = pairingCode,
                                    kaggleUrl = buildKaggleUrl(pairingCode),
                                    timeElapsedSeconds = elapsedSeconds
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Poll attempt failed: ${e.message}")
                }

                delay(pollIntervalSeconds * 1000L)
                elapsedSeconds += pollIntervalSeconds
            }

            if (isActive) {
                _state.value = KaggleState.Failed(
                    "Connection timed out. Make sure you clicked \"Run All\" in the Kaggle notebook."
                )
            }
        }
    }
}
