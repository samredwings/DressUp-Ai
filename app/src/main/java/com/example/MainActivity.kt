package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.HomeScreen
import com.example.ui.TryOnViewModel
import com.example.ui.autocolab.UnifiedGpuConnectScreen
import com.example.ui.theme.DressUpTheme
import com.example.worker.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule background cache cleanup
        WorkScheduler.scheduleCacheCleanup(this)

        setContent {
            DressUpTheme {
                val navController = rememberNavController()
                val viewModel: TryOnViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                val tryOnState by viewModel.tryOnState.collectAsState()
                val wardrobeItems by viewModel.wardrobeItems.collectAsState()
                val wardrobeCount by viewModel.wardrobeCount.collectAsState()
                val recentHistory by viewModel.recentHistory.collectAsState()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            uiState = uiState,
                            tryOnState = tryOnState,
                            wardrobeCount = wardrobeCount,
                            recentHistory = recentHistory,
                            onConnectGpu = {
                                navController.navigate("unified-gpu")
                            },
                            onTryOn = { personImage, garmentImage ->
                                viewModel.startTryOn(personImage, garmentImage)
                            },
                            onWardrobe = {
                                navController.navigate("wardrobe")
                            },
                            onHistory = {
                                navController.navigate("history")
                            },
                            onCategorySelected = { category ->
                                viewModel.selectCategory(category)
                            },
                            onResetTryOn = {
                                viewModel.resetTryOn()
                            }
                        )
                    }

                    composable("unified-gpu") {
                        UnifiedGpuConnectScreen(
                            unifiedManager = viewModel.getGpuManager(),
                            onConnected = {
                                navController.popBackStack()
                            },
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("wardrobe") {
                        SimpleListScreen(
                            title = "My Wardrobe",
                            emptyMessage = "Your saved clothing items will appear here.",
                            rows = wardrobeItems.map { "${it.name} · ${it.category}" },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("history") {
                        SimpleListScreen(
                            title = "Try-On History",
                            emptyMessage = "Your successful try-ons will appear here.",
                            rows = recentHistory.map {
                                "${it.garmentName.ifBlank { it.category }} · ${it.backendUsed} · ${it.processingTimeMs} ms"
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun SimpleListScreen(
    title: String,
    emptyMessage: String,
    rows: List<String>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(emptyMessage, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Text(row, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
