package com.example.engine.autocolab

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AUTO-COLAB MANAGER
 *
 * Handles the entire "one-tap" Google Colab free GPU setup:
 *
 * FLOW:
 * 1. User taps "Connect Free GPU" button
 * 2. App generates a cryptographically random 256-bit pairing token
 * 3. App opens Colab in browser with pre-loaded notebook + pairing code
 * 4. User clicks "Run All" in Colab (one button click)
 * 5. Colab notebook auto-starts, creates ngrok tunnel
 * 6. Colab notebook POSTS ngrok URL to pairing server with the pairing code
 * 7. App polls the pairing server every 3 seconds
 * 8. When URL is received, app auto-configures the backend
 * 9. User sees "Connected!" and can start using AI try-on
 *
 * The user only does: Tap button -> Click Run All in Colab -> Wait ~2 min -> Done
 *
 * PAIRING SERVER:
 * Uses the Deno Deploy service in server/pairing_server.js. Status checks use
 * a bearer header, and the returned tunnel URL is validated before use.
 *
 * COST: $0 for everything
 */
class AutoColabManager(private val context: Context) {

    companion object {
        private const val TAG = "AutoColabManager"

        // Pairing server URL - replace with your own after deploying pairing_server.js
        // Default uses a demo endpoint. For production, deploy your own (see PAIRING_SERVER_SETUP.md)
        private const val DEFAULT_PAIRING_SERVER = "https://dressup-pairing.deno.dev"

        // GitHub-hosted notebook URL (public repo)
        // The notebook is hosted on GitHub and opened via Colab
        private const val NOTEBOOK_GITHUB_URL =
            "https://github.com/samredwings/dressup-colab-backend/blob/main/DressUp_TryOn_Backend.ipynb"

        // Colab opens notebooks from GitHub with this URL pattern
        private const val COLAB_OPEN_URL =
            "https://colab.research.google.com/github/samredwings/dressup-colab-backend/blob/main/DressUp_TryOn_Backend.ipynb"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ---------------------------------------------------------------- state

    sealed class ConnectionState {
        object Idle : ConnectionState()
        object GeneratingCode : ConnectionState()
        data class AwaitingColab(
            val pairingCode: String,
            val colabUrl: String,
            val timeElapsedSeconds: Int
        ) : ConnectionState()
        object PollingForUrl : ConnectionState()
        data class Connected(
            val endpointUrl: String,
            val gpuName: String,
            val accessToken: String? = null
        ) : ConnectionState()
        data class Failed(val reason: String) : ConnectionState()
        object Cancelled : ConnectionState()
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state

    private var pollingJob: Job? = null

    // ---------------------------------------------------------------- public API

    /**
     * Starts the auto-Colab connection flow.
     *
     * Call this when user taps "Connect Free GPU".
     * It will:
     * 1. Generate a pairing code
     * 2. Open Colab in browser with the notebook
     * 3. Start polling for the ngrok URL
     */
    fun startConnectionFlow(pairingServerUrl: String = DEFAULT_PAIRING_SERVER) {
        // Cancel any existing flow
        cancelConnection()

        scope.launch {
            try {
                _state.value = ConnectionState.GeneratingCode

                // Generate an unguessable, short-lived pairing bearer token.
                val pairingCode = PairingSecurity.newToken()

                // Build the Colab URL with the pairing code as a URL fragment
                // The notebook JavaScript reads this from the URL
                val colabUrl = buildColabUrl(pairingCode, pairingServerUrl)

                // Open Colab in browser (Chrome Custom Tabs for smooth experience)
                withContext(Dispatchers.Main) {
                    openColabInBrowser(colabUrl)
                }

                // Start polling for the ngrok URL
                _state.value = ConnectionState.AwaitingColab(
                    pairingCode = pairingCode,
                    colabUrl = colabUrl,
                    timeElapsedSeconds = 0
                )

                startPolling(pairingCode, pairingServerUrl)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start connection flow", e)
                _state.value = ConnectionState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Cancels the current connection attempt.
     */
    fun cancelConnection() {
        pollingJob?.cancel()
        pollingJob = null
        _state.value = ConnectionState.Cancelled
    }

    /**
     * Resets the state to idle.
     */
    fun reset() {
        pollingJob?.cancel()
        pollingJob = null
        _state.value = ConnectionState.Idle
    }

    /**
     * Returns true if a Colab backend is currently connected.
     */
    fun isConnected(): Boolean = _state.value is ConnectionState.Connected

    /**
     * Gets the connected endpoint URL, or null if not connected.
     */
    fun getEndpointUrl(): String? = (_state.value as? ConnectionState.Connected)?.endpointUrl

    // ---------------------------------------------------------------- private implementation

    /**
     * Builds the Colab URL with pairing parameters embedded.
     *
     * The notebook reads these from the URL using JavaScript:
     * - pairing_code: links Colab to the app
     * - pairing_server: where to POST the ngrok URL
     *
     * We use URL fragment (#) so the parameters are available to the notebook's JS
     * without being sent to Google's servers.
     */
    private fun buildColabUrl(pairingCode: String, pairingServerUrl: String): String {
        val params = JSONObject().apply {
            put("pairing_code", pairingCode)
            put("pairing_server", pairingServerUrl)
        }.toString()

        // Base64 encode the params to keep the URL clean
        val encodedParams = android.util.Base64.encodeToString(
            params.toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )

        return "$COLAB_OPEN_URL#config=$encodedParams"
    }

    /**
     * Opens Colab in Chrome Custom Tabs for a smooth in-app experience.
     * Falls back to regular browser if Custom Tabs not available.
     */
    private fun openColabInBrowser(url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (_: Exception) {
            // Fallback to regular browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Polls the pairing server for the ngrok URL.
     * The Colab notebook will POST its URL here when ready.
     */
    private fun startPolling(pairingCode: String, pairingServerUrl: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var elapsedSeconds = 0
            val maxWaitSeconds = 600  // 10 minutes max (Colab takes ~2-3 min to start)
            val pollIntervalSeconds = 3

            while (isActive && elapsedSeconds < maxWaitSeconds) {
                try {
                    val statusUrl = "${pairingServerUrl.removeSuffix("/")}/status"
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
                                val gpuName = json.optString("gpu", "Unknown")
                                _state.value = ConnectionState.Connected(endpointUrl, gpuName, pairingCode)
                                return@launch
                            }
                            "error" -> {
                                val errorMsg = json.optString("message", "Colab reported an error")
                                _state.value = ConnectionState.Failed(errorMsg)
                                return@launch
                            }
                            else -> {
                                // Still waiting
                                _state.value = ConnectionState.AwaitingColab(
                                    pairingCode = pairingCode,
                                    colabUrl = buildColabUrl(pairingCode, pairingServerUrl),
                                    timeElapsedSeconds = elapsedSeconds
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Poll attempt failed: ${e.message}")
                    // Continue polling, don't fail on network errors
                }

                delay(pollIntervalSeconds * 1000L)
                elapsedSeconds += pollIntervalSeconds
            }

            // Timed out
            if (isActive) {
                _state.value = ConnectionState.Failed(
                    "Connection timed out. Make sure you clicked \"Run All\" in Colab."
                )
            }
        }
    }

    /**
     * Manually submit an endpoint URL (for advanced users or testing).
     */
    fun submitManualEndpoint(url: String) {
        _state.value = ConnectionState.Connected(url, "Manual", null)
    }

    fun cleanup() {
        pollingJob?.cancel()
        scope.cancel()
    }
}
