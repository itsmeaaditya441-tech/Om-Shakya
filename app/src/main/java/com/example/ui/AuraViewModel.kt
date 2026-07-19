package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AuraDatabase
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.data.GeneratedImage
import com.example.data.AuraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuraViewModel(
    application: Application,
    private val repository: AuraRepository
) : AndroidViewModel(application) {

    // --- Tab Navigation ---
    private val _currentTab = MutableStateFlow(Tab.CHAT)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    fun selectTab(tab: Tab) {
        _currentTab.value = tab
    }

    // --- Chat Session List ---
    val chatSessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Selected Chat Messages ---
    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Chat Input & Loading States ---
    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    fun updateChatInput(input: String) {
        _chatInput.value = input
    }

    fun clearChatError() {
        _chatError.value = null
    }

    // --- Image Generator States ---
    private val _imagePrompt = MutableStateFlow("")
    val imagePrompt: StateFlow<String> = _imagePrompt.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow("1:1")
    val selectedAspectRatio: StateFlow<String> = _selectedAspectRatio.asStateFlow()

    private val _isImageLoading = MutableStateFlow(false)
    val isImageLoading: StateFlow<Boolean> = _isImageLoading.asStateFlow()

    private val _latestGeneratedImage = MutableStateFlow<String?>(null)
    val latestGeneratedImage: StateFlow<String?> = _latestGeneratedImage.asStateFlow()

    private val _imageError = MutableStateFlow<String?>(null)
    val imageError: StateFlow<String?> = _imageError.asStateFlow()

    fun updateImagePrompt(prompt: String) {
        _imagePrompt.value = prompt
    }

    fun selectAspectRatio(ratio: String) {
        _selectedAspectRatio.value = ratio
    }

    fun clearImageError() {
        _imageError.value = null
    }

    // --- Saved creations (Images & Chat archives) ---
    val savedImages: StateFlow<List<GeneratedImage>> = repository.allImages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _previewImage = MutableStateFlow<GeneratedImage?>(null)
    val previewImage: StateFlow<GeneratedImage?> = _previewImage.asStateFlow()

    fun setPreviewImage(image: GeneratedImage?) {
        _previewImage.value = image
    }

    // --- Initialization & Workflows ---
    init {
        // Automatically open or create a chat session on start
        viewModelScope.launch {
            repository.allSessions.collect { sessions ->
                if (_currentSessionId.value == null) {
                    if (sessions.isNotEmpty()) {
                        _currentSessionId.value = sessions.first().id
                    } else {
                        startNewChat()
                    }
                }
            }
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            val newSessionId = repository.createSession("New Chat")
            _currentSessionId.value = newSessionId
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
    }

    // --- Actions ---
    fun sendChatMessage() {
        val prompt = _chatInput.value.trim()
        if (prompt.isEmpty() || _isChatLoading.value) return

        val sessionId = _currentSessionId.value ?: return

        _chatInput.value = ""
        _isChatLoading.value = true
        _chatError.value = null

        viewModelScope.launch {
            val history = currentMessages.value
            val result = repository.generateChatResponse(sessionId, prompt, history)
            _isChatLoading.value = false
            if (result.isFailure) {
                _chatError.value = result.exceptionOrNull()?.message ?: "Failed to get response"
            }
        }
    }

    fun generateImage() {
        val prompt = _imagePrompt.value.trim()
        if (prompt.isEmpty() || _isImageLoading.value) return

        _isImageLoading.value = true
        _imageError.value = null
        _latestGeneratedImage.value = null

        viewModelScope.launch {
            val result = repository.generateImage(prompt, _selectedAspectRatio.value)
            _isImageLoading.value = false
            if (result.isSuccess) {
                _latestGeneratedImage.value = result.getOrNull()
            } else {
                _imageError.value = result.exceptionOrNull()?.message ?: "Failed to generate image"
            }
        }
    }

    fun deleteSavedImage(imageId: Long) {
        viewModelScope.launch {
            repository.deleteImage(imageId)
            if (_previewImage.value?.id == imageId) {
                _previewImage.value = null
            }
        }
    }
}

enum class Tab {
    CHAT, IMAGE, SAVED
}

@Suppress("UNCHECKED_CAST")
class AuraViewModelFactory(
    private val application: Application,
    private val repository: AuraRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuraViewModel::class.java)) {
            return AuraViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
