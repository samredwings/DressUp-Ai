package com.example.ui.autocolab

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.engine.autocolab.UnifiedGpuManager
import kotlinx.coroutines.launch

/**
 * UNIFIED FREE GPU CONNECTION SCREEN
 *
 * One screen to rule them all. Shows all available free GPU backends,
 * auto-connects to the best one, and lets users manually pick alternatives.
 *
 * USER FLOW:
 * 1. User taps "Auto-Connect Best Free GPU" (one button)
 * 2. App scans all backends in parallel
 * 3. Picks the best one automatically
 * 4. Connects and shows "Connected!" with GPU info
 * 5. User can also manually select any backend from the list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedGpuConnectScreen(
    unifiedManager: UnifiedGpuManager,
    onConnected: () -> Unit,
    onBack: () -> Unit
) {
    val state by unifiedManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Free GPU Power") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val currentState = state) {
                is UnifiedGpuManager.UnifiedState.Idle -> {
                    IdleContent(
                        onAutoConnect = {
                            scope.launch { unifiedManager.autoConnect() }
                        },
                        onManualSelect = {
                            // Will show backend list in a future update
                        }
                    )
                }

                is UnifiedGpuManager.UnifiedState.Scanning -> {
                    ScanningContent()
                }

                is UnifiedGpuManager.UnifiedState.ScanResults -> {
                    ScanResultsContent(
                        statuses = currentState.availableBackends,
                        recommendedId = currentState.recommendedBackend,
                        onSelectBackend = { backendId ->
                            scope.launch { unifiedManager.connectToBackend(backendId) }
                        },
                        onAutoConnect = {
                            scope.launch { unifiedManager.autoConnect() }
                        }
                    )
                }

                is UnifiedGpuManager.UnifiedState.Connecting -> {
                    ConnectingContent(
                        backendName = currentState.backendName,
                        step = currentState.step
                    )
                }

                is UnifiedGpuManager.UnifiedState.Connected -> {
                    ConnectedContent(
                        backend = currentState.backend,
                        endpointUrl = currentState.endpointUrl,
                        gpuName = currentState.gpuName,
                        onContinue = onConnected
                    )
                }

                is UnifiedGpuManager.UnifiedState.Failed -> {
                    FailedContent(
                        reason = currentState.reason,
                        fallbackOptions = currentState.fallbackOptions,
                        onRetry = {
                            scope.launch {
                                unifiedManager.reset()
                                unifiedManager.autoConnect()
                            }
                        },
                        onSelectFallback = { backendId ->
                            scope.launch { unifiedManager.connectToBackend(backendId) }
                        },
                        onBack = {
                            unifiedManager.reset()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    onAutoConnect: () -> Unit,
    onManualSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(vertical = 32.dp)
    ) {
        // Animated GPU icon
        Icon(
            imageVector = Icons.Default.Memory,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "Free GPU Power",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        Text(
            "Get photorealistic AI try-on using free GPUs from multiple providers. The app automatically finds and connects to the best available option.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Available backends card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Available Free Backends",
                    style = MaterialTheme.typography.titleMedium
                )

                BackendListItem(
                    icon = Icons.Default.Computer,
                    name = "Your PC",
                    detail = "Unlimited, uncensored, best quality",
                    isBest = true
                )
                BackendListItem(
                    icon = Icons.Default.Science,
                    name = "Kaggle",
                    detail = "NVIDIA P100, 30 hrs/week"
                )
                BackendListItem(
                    icon = Icons.Default.Cloud,
                    name = "Google Colab",
                    detail = "NVIDIA T4, 12 hrs/day"
                )
                BackendListItem(
                    icon = Icons.Default.FlashOn,
                    name = "Hugging Face",
                    detail = "No setup, instant, 1,000 req/month"
                )
                BackendListItem(
                    icon = Icons.Default.Bolt,
                    name = "Lightning AI",
                    detail = "Free GPU credits"
                )
                BackendListItem(
                    icon = Icons.Default.Storage,
                    name = "Modal Labs",
                    detail = "Free serverless GPU"
                )
                BackendListItem(
                    icon = Icons.Default.DeveloperBoard,
                    name = "RunPod",
                    detail = "Free tier GPU"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main action button
        Button(
            onClick = onAutoConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Auto-Connect Best Free GPU",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            "100% Free. No credit card. No signup required for most options.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScanningContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(vertical = 48.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            strokeWidth = 4.dp
        )

        Text(
            "Scanning Free GPU Backends...",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            "Checking all available options in parallel",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        // Scanning items
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScanItem("Checking your PC...")
            ScanItem("Checking Kaggle...")
            ScanItem("Checking Google Colab...")
            ScanItem("Checking Hugging Face...")
            ScanItem("Checking Lightning AI...")
            ScanItem("Checking Modal Labs...")
            ScanItem("Checking RunPod...")
        }
    }
}

@Composable
private fun ScanResultsContent(
    statuses: List<UnifiedGpuManager.BackendStatus>,
    recommendedId: String?,
    onSelectBackend: (String) -> Unit,
    onAutoConnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Available Backends",
            style = MaterialTheme.typography.headlineSmall
        )

        statuses.forEach { status ->
            val isRecommended = status.info.id == recommendedId
            BackendStatusCard(
                status = status,
                isRecommended = isRecommended,
                onSelect = { onSelectBackend(status.info.id) }
            )
        }

        Button(
            onClick = onAutoConnect,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Auto-Connect to Best")
        }
    }
}

@Composable
private fun ConnectingContent(backendName: String, step: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(vertical = 48.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))

        Text(
            "Connecting to $backendName",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            step,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConnectedContent(
    backend: UnifiedGpuManager.BackendInfo,
    endpointUrl: String,
    gpuName: String,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(vertical = 32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "Connected!",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(label = "Backend", value = backend.name)
                InfoRow(label = "GPU", value = gpuName)
                InfoRow(label = "Daily Limit", value = backend.dailyHours)
                InfoRow(label = "Cost", value = "Free")
                InfoRow(label = "Status", value = "Ready")
            }
        }

        Text(
            "You can now use photorealistic AI try-on with free GPU power!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Start Using AI Try-On", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FailedContent(
    reason: String,
    fallbackOptions: List<String>,
    onRetry: () -> Unit,
    onSelectFallback: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Text(
            "Connection Failed",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                reason,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        if (fallbackOptions.isNotEmpty()) {
            Text(
                "Try these alternatives:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start)
            )

            fallbackOptions.forEach { backendId ->
                val backend = UnifiedGpuManager.BACKENDS.find { it.id == backendId }
                if (backend != null) {
                    OutlinedButton(
                        onClick = { onSelectFallback(backendId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try ${backend.name}")
                    }
                }
            }
        }

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go Back")
        }
    }
}

// ---------------------------------------------------------------- helper composables

@Composable
private fun BackendListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    detail: String,
    isBest: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                if (isBest) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            " BEST ",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BackendStatusCard(
    status: UnifiedGpuManager.BackendStatus,
    isRecommended: Boolean,
    onSelect: () -> Unit
) {
    val cardColor = when {
        status.isActive -> MaterialTheme.colorScheme.primaryContainer
        status.isAvailable -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status.info.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge {
                            Text("Recommended")
                        }
                    }
                }
                Text(
                    status.info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "GPU: ${status.info.gpu} | Daily: ${status.info.dailyHours}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (status.latencyMs > 0) {
                    Text(
                        "Latency: ${status.latencyMs}ms",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (status.lastError != null) {
                    Text(
                        "Error: ${status.lastError}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            when {
                status.isActive -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                status.isAvailable -> {
                    Icon(
                        Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Available",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Unavailable",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
