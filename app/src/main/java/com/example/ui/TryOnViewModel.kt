package com.example.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TryOnHistoryEntity
import com.example.data.TryOnHistoryDao
import com.example.data.WardrobeDao
import com.example.data.WardrobeItemEntity
import com.example.engine.TryOnPipelineV3
import com.example.engine.TryOnState
import com.example.engine.autocolab.UnifiedGpuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CENTRALIZED VIEWMODEL - Single Source of Truth
 *
 * Implements Unidirectional Data Flow (UDF):
 * - All UI state lives in this ViewModel
 * - UI only observes StateFlow and sends events back
 * - No local state in Composables
 */
@HiltViewModel
class TryOnViewModel @Inject constructor(
    private val pipeline: TryOnPipelineV3,
    private val wardrobeDao: WardrobeDao,
    private val historyDao: TryOnHistoryDao,
    private val gpuManager: UnifiedGpuManager
) : ViewModel() {

    // ---------------------------------------------------------------- pipeline state
    private val _tryOnState = MutableStateFlow<TryOnState>(TryOnState.Idle)
    val tryOnState: StateFlow<TryOnState> = _tryOnState.asStateFlow()

    // ---------------------------------------------------------------- UI state
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // ---------------------------------------------------------------- reactive data from Room
    val wardrobeItems = wardrobeDao.getAllItemsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wardrobeCount = wardrobeDao.getItemCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentHistory = historyDao.getRecentSuccessFlow(limit = 10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyCount = historyDao.getHistoryCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ---------------------------------------------------------------- GPU state
    val gpuState = gpuManager.state

    // ---------------------------------------------------------------- init
    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // Any async init here
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    // ---------------------------------------------------------------- user actions

    fun startTryOn(personImage: Bitmap, garmentImage: Bitmap, preferCloud: Boolean = false) {
        viewModelScope.launch {
            _tryOnState.value = TryOnState.Preprocessing("Starting...", 0f)

            pipeline.executeTryOn(personImage, garmentImage, preferCloud = preferCloud)
                .collect { state ->
                    _tryOnState.value = state

                    // Persist successful results to history
                    if (state is TryOnState.Success) {
                        val historyItem = TryOnHistoryEntity(
                            personImageUri = "", // Would be actual URI in production
                            garmentImageUri = "",
                            resultImageUri = state.resultUri,
                            backendUsed = state.backendUsed,
                            processingTimeMs = state.processingTimeMs,
                            success = true
                        )
                        historyDao.insertHistory(historyItem)
                    }
                }
        }
    }

    fun resetTryOn() {
        _tryOnState.value = TryOnState.Idle
    }

    fun addToWardrobe(name: String, category: String, uri: String) {
        viewModelScope.launch {
            val item = WardrobeItemEntity(
                name = name,
                category = category,
                localUri = uri
            )
            wardrobeDao.insertItem(item)
        }
    }

    fun removeFromWardrobe(item: WardrobeItemEntity) {
        viewModelScope.launch {
            wardrobeDao.deleteItem(item)
        }
    }

    fun autoConnectGpu() {
        gpuManager.autoConnect()
    }

    fun disconnectGpu() {
        gpuManager.reset()
    }

    fun getGpuManager(): UnifiedGpuManager = gpuManager

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    // ---------------------------------------------------------------- cleanup
    override fun onCleared() {
        super.onCleared()
        pipeline.cleanup()
    }
}

/**
 * Immutable UI state for HomeScreen.
 * Single source of truth for all non-domain UI state.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val selectedCategory: String = "All",
    val errorMessage: String? = null,
    val isGpuConnected: Boolean = false,
    val gpuBackendName: String = ""
)
