package com.example.ui.autocolab

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.engine.autocolab.AutoColabManager
import kotlinx.coroutines.launch

/**
 * ONE-TAP GOOGLE COLAB FREE GPU CONNECTION SCREEN
 *
 * User flow:
 * 1. User taps "Connect Free GPU" button
 * 2. App generates pairing code, opens Colab in browser
 * 3. User clicks "Run All" in Colab (one button)
 * 4. App auto-detects connection, shows "Connected!"
 * 5. User can now use photorealistic AI try-on
 *
 * The user does NOT need to:
 * - Copy/paste any URLs
 * - Know what ngrok is
 * - Configure anything manually
 * - Understand the technical setup
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoColabConnectScreen(
    autoColabManager: AutoColabManager,
    onConnected: () -> Unit,
    onBack: () -> Unit
) {
    val state by autoColabManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Free GPU Connection") },
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
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            when (val currentState = state) {
                is AutoColabManager.ConnectionState.Idle -> {
                    IdleContent(
                        onConnect = {
                            scope.launch {
                                autoColabManager.startConnectionFlow()
                            }
                        }
                    )
                }

                is AutoColabManager.ConnectionState.GeneratingCode -> {
                    GeneratingCodeContent()
                }

                is AutoColabManager.ConnectionState.AwaitingColab -> {
                    AwaitingColabContent(
                        timeElapsed = currentState.timeElapsedSeconds,
                        onCancel = { autoColabManager.cancelConnection() }
                    )
                }

                is AutoColabManager.ConnectionState.Connected -> {
                    ConnectedContent(
                        endpointUrl = currentState.endpointUrl,
                        gpuName = currentState.gpuName,
                        onContinue = onConnected
                    )
                }

                is AutoColabManager.ConnectionState.Failed -> {
                    FailedContent(
                        reason = currentState.reason,
                        onRetry = {
                            scope.launch {
                                autoColabManager.reset()
                                autoColabManager.startConnectionFlow()
                            }
                        },
                        onBack = { autoColabManager.reset() }
                    )
                }

                is AutoColabManager.ConnectionState.Cancelled -> {
                    IdleContent(
                        onConnect = {
                            scope.launch {
                                autoColabManager.reset()
                                autoColabManager.startConnectionFlow()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(onConnect: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // GPU Icon
        Icon(
            imageVector = Icons.Default.Memory,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "Free GPU Power",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            "Get photorealistic AI try-on results using Google's free GPU in the cloud.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Steps
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
                StepItem(number = "1", text = "Tap the button below")
                StepItem(number = "2", text = "Colab opens in your browser")
                StepItem(number = "3", text = "Click \"Run All\" (one button)")
                StepItem(number = "4", text = "Wait 2-3 minutes - app connects automatically!")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.RocketLaunch, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Connect Free GPU", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            "100% Free. No credit card needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GeneratingCodeContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))

        Text(
            "Preparing Connection...",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            "Generating a secure pairing code...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AwaitingColabContent(
    timeElapsed: Int,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Do not display the pairing token: it authorizes registration and status access.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Secure pairing token created",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "The app shares it with the notebook automatically. Do not post it or share screenshots of it.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(
                "Waiting for Colab to start...",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        val minutes = timeElapsed / 60
        val seconds = timeElapsed % 60
        Text(
            "Elapsed: ${minutes}m ${seconds}s (usually takes 2-3 minutes)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Progress indicator
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        // Instructions reminder
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "In your browser:",
                    style = MaterialTheme.typography.titleSmall
                )
                Text("1. Colab should be open now")
                Text("2. Click Runtime > Run all (or Ctrl+F9)")
                Text("3. Wait for the notebook to finish")
                Text("4. This screen will update automatically!")
            }
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ConnectedContent(
    endpointUrl: String,
    gpuName: String,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "Connected!",
            style = MaterialTheme.typography.headlineMedium,
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow(label = "GPU", value = gpuName)
                InfoRow(label = "Status", value = "Ready")
                InfoRow(label = "Cost", value = "Free")
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
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
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

        Text(
            "Common fixes:\n\n" +
            "1. Make sure you clicked \"Run All\" in Colab\n" +
            "2. Check that Colab Runtime is set to GPU\n" +
            "3. Ensure your phone has internet connection\n" +
            "4. Try again - Colab sometimes takes longer",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start
        )

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

@Composable
private fun StepItem(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium
        )
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
