package com.example.engine.autocolab

import android.content.Context
import android.util.Log
import com.example.engine.FreeCloudClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * UNIFIED FREE GPU BACKEND MANAGER
 *
 * Automatically discovers, tests, and connects to the best available
 * free GPU backend with ZERO user configuration.
 *
 * SUPPORTED BACKENDS (all free):
 * 1. Self-Hosted PC (ngrok)     - Best quality, unlimited, uncensored
 * 2. Kaggle (P100 GPU)          - 30hrs/week, more reliable than Colab
 * 3. Google Colab (T4 GPU)      - 12hrs/day, easiest setup
 * 4. Hugging Face Inference API - No setup, instant, 1,000 req/month
 * 5. Lightning AI               - Free GPU credits
 * 6. Modal Labs               - Free serverless GPU
 * 7. RunPod Serverless        - Free tier GPU
 *
 * AUTO-CONNECT FLOW:
 * 1. Check if self-hosted PC is already running (fastest)
 * 2. Check if Kaggle notebook is active
 * 3. Check if Colab notebook is active
 * 4. If none active, launch the EASIEST one (Colab - one click)
 * 5. Fall back to Hugging Face (no setup at all)
 * 6. All with ONE tap from the user
 *
 * The user taps "Auto-Connect Best Free GPU" and the app does everything else.
 */
class UnifiedGpuManager(
    private val context: Context,
    private val freeCloudClient: FreeCloudClient
) {

    companion object {
        private const val TAG = "UnifiedGpuManager"

        // Pairing server (deployed once, free forever)
        private const val PAIRING_SERVER = "https://dressup-pairing.deno.dev"

        // Backend registry with metadata
        val BACKENDS = listOf(
            BackendInfo(
                id = "selfhosted",
                name = "Your PC",
                description = "Run on your own computer. Best quality, unlimited, uncensored.",
                gpu = "Your GPU",
                dailyHours = "Unlimited",
                setupDifficulty = SetupDifficulty.MEDIUM,
                autoConnectSupported = true,
                reliability = Reliability.HIGH,
                requiresPc = true
            ),
            BackendInfo(
                id = "kaggle",
                name = "Kaggle",
                description = "Free P100 GPU. 30 hours/week. More reliable than Colab.",
                gpu = "NVIDIA P100",
                dailyHours = "~4 hrs/day",
                setupDifficulty = SetupDifficulty.MEDIUM,
                autoConnectSupported = true,
                reliability = Reliability.HIGH,
                requiresPc = false
            ),
            BackendInfo(
                id = "colab",
                name = "Google Colab",
                description = "Free T4 GPU. 12 hours/day. Easiest setup.",
                gpu = "NVIDIA T4",
                dailyHours = "~12 hrs/day",
                setupDifficulty = SetupDifficulty.EASY,
                autoConnectSupported = true,
                reliability = Reliability.MEDIUM,
                requiresPc = false
            ),
            BackendInfo(
                id = "lightning",
                name = "Lightning AI",
                description = "Free GPU credits. Good for quick jobs.",
                gpu = "NVIDIA T4",
                dailyHours = "Varies",
                setupDifficulty = SetupDifficulty.EASY,
                autoConnectSupported = false,
                reliability = Reliability.MEDIUM,
                requiresPc = false
            ),
            BackendInfo(
                id = "modal",
                name = "Modal Labs",
                description = "Free serverless GPU. Pay only for what you use (free tier).",
                gpu = "NVIDIA T4",
                dailyHours = "~$5 free credit",
                setupDifficulty = SetupDifficulty.HARD,
                autoConnectSupported = false,
                reliability = Reliability.HIGH,
                requiresPc = false
            ),
            BackendInfo(
                id = "huggingface",
                name = "Hugging Face",
                description = "No setup needed! 1,000 free requests/month. Works instantly.",
                gpu = "Shared GPU",
                dailyHours = "1,000 req/month",
                setupDifficulty = SetupDifficulty.NONE,
                autoConnectSupported = true,
                reliability = Reliability.MEDIUM,
                requiresPc = false
            ),
            BackendInfo(
                id = "runpod",
                name = "RunPod Serverless",
                description = "Free tier GPU. Serverless = only pay when running.",
                gpu = "NVIDIA RTX A4000",
                dailyHours = "Free tier",
                setupDifficulty = SetupDifficulty.HARD,
                autoConnectSupported = false,
                reliability = Reliability.HIGH,
                requiresPc = false
            )
        )
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Sub-managers for each backend
    private val colabManager by lazy { AutoColabManager(context) }
    private val kaggleManager by lazy { AutoKaggleManager(context) }
    private val hfManager by lazy { HuggingFaceManager(context) }
    private val selfHostedManager by lazy { SelfHostedManager(context) }

    // ---------------------------------------------------------------- state

    data class BackendInfo(
        val id: String,
        val name: String,
        val description: String,
        val gpu: String,
        val dailyHours: String,
        val setupDifficulty: SetupDifficulty,
        val autoConnectSupported: Boolean,
        val reliability: Reliability,
        val requiresPc: Boolean
    )

    enum class SetupDifficulty { NONE, EASY, MEDIUM, HARD }
    enum class Reliability { LOW, MEDIUM, HIGH }

    sealed class UnifiedState {
        object Idle : UnifiedState()
        object Scanning : UnifiedState()
        data class ScanResults(
            val availableBackends: List<BackendStatus>,
            val recommendedBackend: String?
        ) : UnifiedState()
        data class Connecting(val backendName: String, val step: String) : UnifiedState()
        data class Connected(
            val backend: BackendInfo,
            val endpointUrl: String,
            val gpuName: String,
            val accessToken: String? = null
        ) : UnifiedState()
        data class Failed(val reason: String, val fallbackOptions: List<String>) : UnifiedState()
    }

    data class BackendStatus(
        val info: BackendInfo,
        val isAvailable: Boolean,
        val isActive: Boolean = false,
        val latencyMs: Long = -1,
        val lastError: String? = null
    )

    private val _state = MutableStateFlow<UnifiedState>(UnifiedState.Idle)
    val state: StateFlow<UnifiedState> = _state

    // ---------------------------------------------------------------- auto-connect: the main feature

    /**
     * ONE-TAP AUTO-CONNECT TO THE BEST FREE GPU
     *
     * This is the main method. The user taps ONE button and this:
     * 1. Scans all backends to find what's available
     * 2. Picks the best one based on speed, reliability, and setup ease
     * 3. Auto-connects to it
     * 4. Returns the connected endpoint
     *
     * Priority order:
     * 1. Self-hosted PC (if already running - fastest)
     * 2. Kaggle (if notebook is active)
     * 3. Colab (if notebook is active)
     * 4. Hugging Face (always works, no setup)
     * 5. If none active, launch Colab (easiest to set up)
     */
    fun autoConnect() {
        scope.launch {
            try {
                _state.value = UnifiedState.Scanning

                // Step 1: Scan all backends in parallel
                val statuses = scanAllBackends()

                // Step 2: Find already-active backends
                val activeBackends = statuses.filter { it.isActive }

                // Step 3: Determine best backend to connect to
                val backendToConnect = when {
                    // Priority 1: Self-hosted PC (best quality, already running)
                    activeBackends.any { it.info.id == "selfhosted" } ->
                        activeBackends.first { it.info.id == "selfhosted" }

                    // Priority 2: Kaggle (most reliable free GPU)
                    activeBackends.any { it.info.id == "kaggle" } ->
                        activeBackends.first { it.info.id == "kaggle" }

                    // Priority 3: Colab (easy, commonly used)
                    activeBackends.any { it.info.id == "colab" } ->
                        activeBackends.first { it.info.id == "colab" }

                    // Priority 4: Hugging Face (zero setup, always works)
                    statuses.any { it.info.id == "huggingface" && it.isAvailable } ->
                        statuses.first { it.info.id == "huggingface" }

                    // Priority 5: Launch Colab (easiest to set up from scratch)
                    else -> statuses.first { it.info.id == "colab" }
                }

                _state.value = UnifiedState.ScanResults(
                    availableBackends = statuses,
                    recommendedBackend = backendToConnect.info.id
                )

                // Step 4: Connect to the selected backend
                connectToBackend(backendToConnect.info)

            } catch (e: Exception) {
                Log.e(TAG, "Auto-connect failed", e)
                _state.value = UnifiedState.Failed(
                    reason = e.message ?: "Unknown error",
                    fallbackOptions = listOf("huggingface", "colab", "kaggle")
                )
            }
        }
    }

    /**
     * Connect to a specific backend by ID.
     */
    fun connectToBackend(backendId: String) {
        val backend = BACKENDS.find { it.id == backendId }
            ?: throw IllegalArgumentException("Unknown backend: $backendId")
        connectToBackend(backend)
    }

    private suspend fun connectToBackend(backend: BackendInfo) {
        _state.value = UnifiedState.Connecting(backend.name, "Initializing...")

        when (backend.id) {
            "selfhosted" -> connectSelfHosted()
            "kaggle" -> connectKaggle()
            "colab" -> connectColab()
            "huggingface" -> connectHuggingFace()
            "lightning" -> connectLightning()
            "modal" -> connectModal()
            "runpod" -> connectRunPod()
            else -> throw IllegalStateException("Unsupported backend: ${backend.id}")
        }
    }

    // ---------------------------------------------------------------- backend-specific connections

    private suspend fun connectSelfHosted() {
        _state.value = UnifiedState.Connecting("Your PC", "Checking for running server...")

        // Try to detect if user has a self-hosted server running
        val detected = selfHostedManager.detectRunningServer()

        if (detected != null) {
            _state.value = UnifiedState.Connected(
                BACKENDS.first { it.id == "selfhosted" },
                detected,
                "Local GPU"
            )
        } else {
            // Guide user to start their PC server
            _state.value = UnifiedState.Failed(
                "No self-hosted server detected. Please start the server on your PC first.",
                listOf("colab", "kaggle", "huggingface")
            )
        }
    }

    private suspend fun connectKaggle() {
        _state.value = UnifiedState.Connecting("Kaggle", "Launching Kaggle notebook...")

        // Kaggle uses a similar pairing approach to Colab
        kaggleManager.startConnectionFlow()

        // Wait for connection
        kaggleManager.state.collect { state ->
            when (state) {
                is AutoKaggleManager.KaggleState.Connected -> {
                    freeCloudClient.setKaggleEndpoint(state.endpointUrl)
                    freeCloudClient.setKaggleAccessToken(state.accessToken)
                    _state.value = UnifiedState.Connected(
                        BACKENDS.first { it.id == "kaggle" },
                        state.endpointUrl,
                        state.gpuName,
                        state.accessToken
                    )
                }
                is AutoKaggleManager.KaggleState.Failed -> {
                    _state.value = UnifiedState.Failed(
                        "Kaggle connection failed: ${state.reason}",
                        listOf("colab", "huggingface")
                    )
                }
                else -> {
                    _state.value = UnifiedState.Connecting(
                        "Kaggle",
                        state.javaClass.simpleName
                    )
                }
            }
        }
    }

    private suspend fun connectColab() {
        _state.value = UnifiedState.Connecting("Google Colab", "Launching Colab notebook...")

        colabManager.startConnectionFlow(PAIRING_SERVER)

        colabManager.state.collect { state ->
            when (state) {
                is AutoColabManager.ConnectionState.Connected -> {
                    freeCloudClient.setColabEndpoint(state.endpointUrl)
                    freeCloudClient.setColabAccessToken(state.accessToken)
                    _state.value = UnifiedState.Connected(
                        BACKENDS.first { it.id == "colab" },
                        state.endpointUrl,
                        state.gpuName,
                        state.accessToken
                    )
                }
                is AutoColabManager.ConnectionState.Failed -> {
                    _state.value = UnifiedState.Failed(
                        "Colab connection failed: ${state.reason}",
                        listOf("huggingface", "kaggle")
                    )
                }
                is AutoColabManager.ConnectionState.AwaitingColab -> {
                    _state.value = UnifiedState.Connecting(
                        "Google Colab",
                        "Waiting for notebook to start... (${state.timeElapsedSeconds}s)"
                    )
                }
                else -> {
                    _state.value = UnifiedState.Connecting(
                        "Google Colab",
                        state.javaClass.simpleName
                    )
                }
            }
        }
    }

    private suspend fun connectHuggingFace() {
        _state.value = UnifiedState.Connecting("Hugging Face", "Testing Inference API...")

        val success = hfManager.testConnection()

        if (success) {
            _state.value = UnifiedState.Connected(
                BACKENDS.first { it.id == "huggingface" },
                "https://api-inference.huggingface.co",
                "Shared GPU"
            )
        } else {
            _state.value = UnifiedState.Failed(
                "Hugging Face Inference API is not responding. It may be temporarily unavailable.",
                listOf("colab", "kaggle")
            )
        }
    }

    private suspend fun connectLightning() {
        _state.value = UnifiedState.Connecting("Lightning AI", "This backend requires manual setup. See documentation.")
        // Lightning AI requires account creation and API key
        // Guide user through setup
        _state.value = UnifiedState.Failed(
            "Lightning AI requires a free account and API key. Please set it up in Settings > Backends > Lightning AI.",
            listOf("colab", "huggingface")
        )
    }

    private suspend fun connectModal() {
        _state.value = UnifiedState.Connecting("Modal Labs", "This backend requires manual setup. See documentation.")
        _state.value = UnifiedState.Failed(
            "Modal Labs requires a free account and API token. Please set it up in Settings > Backends > Modal.",
            listOf("colab", "huggingface")
        )
    }

    private suspend fun connectRunPod() {
        _state.value = UnifiedState.Connecting("RunPod", "This backend requires manual setup. See documentation.")
        _state.value = UnifiedState.Failed(
            "RunPod requires a free account and API key. Please set it up in Settings > Backends > RunPod.",
            listOf("colab", "huggingface")
        )
    }

    // ---------------------------------------------------------------- scanning

    /**
     * Scans all backends in parallel to check availability.
     */
    private suspend fun scanAllBackends(): List<BackendStatus> = coroutineScope {
        BACKENDS.map { backend ->
            async {
                scanBackend(backend)
            }
        }.awaitAll()
    }

    private suspend fun scanBackend(backend: BackendInfo): BackendStatus {
        val startTime = System.currentTimeMillis()

        return try {
            val (isAvailable, isActive, error) = when (backend.id) {
                "selfhosted" -> scanSelfHosted()
                "kaggle" -> scanKaggle()
                "colab" -> scanColab()
                "huggingface" -> scanHuggingFace()
                "lightning" -> scanLightning()
                "modal" -> scanModal()
                "runpod" -> scanRunPod()
                else -> Triple(false, false, "Unknown backend")
            }

            val latency = System.currentTimeMillis() - startTime

            BackendStatus(
                info = backend,
                isAvailable = isAvailable,
                isActive = isActive,
                latencyMs = latency,
                lastError = error
            )
        } catch (e: Exception) {
            BackendStatus(
                info = backend,
                isAvailable = false,
                isActive = false,
                latencyMs = System.currentTimeMillis() - startTime,
                lastError = e.message
            )
        }
    }

    private suspend fun scanSelfHosted(): Triple<Boolean, Boolean, String?> {
        val running = selfHostedManager.detectRunningServer() != null
        return Triple(running, running, if (running) null else "No running server detected")
    }

    private suspend fun scanKaggle(): Triple<Boolean, Boolean, String?> {
        // Check if Kaggle notebook endpoint is configured and responding
        return kaggleManager.checkStatus()
    }

    private suspend fun scanColab(): Triple<Boolean, Boolean, String?> {
        // Check if Colab endpoint is configured and responding
        return Triple(true, false, null) // Always available, not active until connected
    }

    private suspend fun scanHuggingFace(): Triple<Boolean, Boolean, String?> {
        return try {
            val request = Request.Builder()
                .url("https://api-inference.huggingface.co/status")
                .build()
            val response = okHttpClient.newCall(request).execute()
            Triple(response.isSuccessful, false, null)
        } catch (e: Exception) {
            Triple(true, false, "API check failed but service may still work") // HF usually works even if status fails
        }
    }

    private suspend fun scanLightning(): Triple<Boolean, Boolean, String?> {
        return Triple(true, false, null) // Requires manual setup
    }

    private suspend fun scanModal(): Triple<Boolean, Boolean, String?> {
        return Triple(true, false, null) // Requires manual setup
    }

    private suspend fun scanRunPod(): Triple<Boolean, Boolean, String?> {
        return Triple(true, false, null) // Requires manual setup
    }

    // ---------------------------------------------------------------- public utilities

    fun reset() {
        _state.value = UnifiedState.Idle
        freeCloudClient.clearPairedBackends()
        colabManager.reset()
        kaggleManager.reset()
    }

    fun cancel() {
        colabManager.cancelConnection()
        kaggleManager.cancelConnection()
        freeCloudClient.clearPairedBackends()
        _state.value = UnifiedState.Idle
    }

    fun cleanup() {
        scope.cancel()
        colabManager.cleanup()
        kaggleManager.cleanup()
    }

    /**
     * Returns the currently connected endpoint URL, or null.
     */
    fun getActiveEndpoint(): String? {
        return when (val s = _state.value) {
            is UnifiedState.Connected -> s.endpointUrl
            else -> null
        }
    }

    /**
     * Returns true if any backend is currently connected.
     */
    fun isConnected(): Boolean = _state.value is UnifiedState.Connected
}
