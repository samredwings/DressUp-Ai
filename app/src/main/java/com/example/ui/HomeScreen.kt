package com.example.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.TryOnHistoryEntity
import com.example.engine.TryOnState
import com.example.ui.theme.Purple40
import com.example.ui.theme.Purple80

/**
 * HOME SCREEN - Production-Grade Market Standard UI
 *
 * Connected to TryOnViewModel via UDF pattern.
 * All state is passed in as parameters; no local business logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    tryOnState: TryOnState,
    wardrobeCount: Int,
    recentHistory: List<TryOnHistoryEntity>,
    onConnectGpu: () -> Unit,
    onTryOn: (Bitmap, Bitmap) -> Unit,
    onWardrobe: () -> Unit,
    onHistory: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onResetTryOn: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selectedCategory by remember { mutableStateOf(uiState.selectedCategory) }
    var showTryOnDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Purple40,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Dress-Up AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onConnectGpu) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = Color(0xFF4CAF50)) {
                                    Text("Free", fontSize = 8.sp)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "GPU"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showTryOnDialog = true },
                icon = { Icon(Icons.Default.CameraAlt, null) },
                text = { Text("AI Try-On") },
                containerColor = Purple40,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .fillMaxSize()
            ) {
                HeroBanner(onTryOn = { showTryOnDialog = true })

                CategoryChips(
                    selected = selectedCategory,
                    onSelect = {
                        selectedCategory = it
                        onCategorySelected(it)
                    }
                )

                FeaturedOutfitsSection()

                QuickActionsGrid(
                    wardrobeCount = wardrobeCount,
                    historyCount = recentHistory.size,
                    onWardrobe = onWardrobe,
                    onHistory = onHistory,
                    onConnectGpu = onConnectGpu
                )

                RecentTryOnsSection(
                    history = recentHistory,
                    onViewAll = onHistory
                )

                HowItWorksSection()

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Pipeline progress overlay
            if (tryOnState !is TryOnState.Idle && tryOnState !is TryOnState.Success && tryOnState !is TryOnState.Error) {
                PipelineProgressOverlay(
                    state = tryOnState,
                    onCancel = onResetTryOn
                )
            }

            // Error snackbar
            if (tryOnState is TryOnState.Error) {
                val error = tryOnState as TryOnState.Error
                ErrorSnackbar(
                    message = error.message,
                    onDismiss = onResetTryOn,
                    onRetry = { /* retry logic */ }
                )
            }
        }
    }
}

@Composable
private fun HeroBanner(onTryOn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Purple40, Purple80.copy(alpha = 0.8f))
                )
            )
            .clickable { onTryOn() }
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "AI Virtual Try-On",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "See clothes on you instantly",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onTryOn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Purple40
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Now", fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 40.dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        )
    }
}

@Composable
private fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    val categories = listOf(
        "All" to Icons.Default.Apps,
        "Dresses" to Icons.Default.Female,
        "Tops" to Icons.Default.Checkroom,
        "Bottoms" to Icons.Default.Male,
        "Outerwear" to Icons.Default.Layers
    )
    LazyRow(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(categories) { (name, icon) ->
            val isSelected = name == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(name) },
                label = { Text(name) },
                leadingIcon = if (isSelected) {
                    { Icon(icon, null, modifier = Modifier.size(18.dp)) }
                } else null,
                modifier = Modifier.height(36.dp)
            )
        }
    }
}

@Composable
private fun FeaturedOutfitsSection() {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Trending Now",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = {}) { Text("See All") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(featuredOutfits) { outfit ->
                OutfitCard(outfit)
            }
        }
    }
}

@Composable
private fun OutfitCard(outfit: OutfitItem) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFFF5F5F5))
            ) {
                Icon(
                    imageVector = Icons.Default.Checkroom,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Purple40
                ) {
                    Text(
                        "AI",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(outfit.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Text(
                    outfit.category,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    wardrobeCount: Int,
    historyCount: Int,
    onWardrobe: () -> Unit,
    onHistory: () -> Unit,
    onConnectGpu: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "Quick Actions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                icon = Icons.Default.Doorbell,
                label = "My Wardrobe",
                sublabel = "$wardrobeCount items",
                color = Color(0xFFE3F2FD),
                iconColor = Color(0xFF1976D2),
                modifier = Modifier.weight(1f),
                onClick = onWardrobe
            )
            QuickActionCard(
                icon = Icons.Default.History,
                label = "History",
                sublabel = "$historyCount try-ons",
                color = Color(0xFFF3E5F5),
                iconColor = Color(0xFF7B1FA2),
                modifier = Modifier.weight(1f),
                onClick = onHistory
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                icon = Icons.Default.Memory,
                label = "Free GPU",
                sublabel = "Connect",
                color = Color(0xFFE8F5E9),
                iconColor = Color(0xFF388E3C),
                modifier = Modifier.weight(1f),
                onClick = onConnectGpu
            )
            QuickActionCard(
                icon = Icons.Default.CloudDownload,
                label = "Models",
                sublabel = "2 available",
                color = Color(0xFFFFF3E0),
                iconColor = Color(0xFFF57C00),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String,
    color: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(28.dp))
            Column {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(sublabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecentTryOnsSection(
    history: List<TryOnHistoryEntity>,
    onViewAll: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent Try-Ons",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onViewAll) { Text("View All") }
        }

        if (history.isEmpty()) {
            EmptyTryOnsPlaceholder()
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history.take(10)) { item ->
                    RecentItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun EmptyTryOnsPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("No try-ons yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Tap the AI Try-On button to start",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun RecentItemCard(item: TryOnHistoryEntity) {
    Card(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFFF5F5F5))
            ) {
                if (item.resultImageUri.isNotEmpty()) {
                    AsyncImage(
                        model = item.resultImageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp),
                        tint = Color.Gray.copy(alpha = 0.4f)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    item.garmentName.ifEmpty { "Try-On #${item.id}" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Text(
                    formatTimeAgo(item.createdAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HowItWorksSection() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "How It Works",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        val steps = listOf(
            Triple("1", "Upload Photo", "Take or select your photo"),
            Triple("2", "Choose Outfit", "Pick from wardrobe or upload"),
            Triple("3", "AI Magic", "Free GPU generates realistic result"),
        )
        steps.forEach { (num, title, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Purple40,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            num,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PipelineProgressOverlay(
    state: TryOnState,
    onCancel: () -> Unit
) {
    val (title, progress) = when (state) {
        is TryOnState.Preprocessing -> state.step to state.progress
        is TryOnState.DetectingBody -> state.step to state.progress
        is TryOnState.Segmenting -> state.step to state.progress
        is TryOnState.GarmentWarping -> state.step to state.progress
        is TryOnState.CloudGenerating -> state.step to state.progress
        is TryOnState.EnhancingImage -> state.step to state.progress
        is TryOnState.SavingResult -> state.step to state.progress
        else -> "Processing..." to 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    // Handled by Scaffold snackbar host in production
    // Simplified inline display for now
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(3000)
        onDismiss()
    }
}

// ---------------------------------------------------------------- data helpers

data class OutfitItem(val id: String, val name: String, val category: String)

private val featuredOutfits = listOf(
    OutfitItem("1", "Summer Floral Dress", "Dresses"),
    OutfitItem("2", "Classic Blue Jeans", "Bottoms"),
    OutfitItem("3", "Silk Evening Gown", "Dresses"),
    OutfitItem("4", "Casual Blazer", "Outerwear"),
    OutfitItem("5", "Bohemian Top", "Tops")
)

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
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
