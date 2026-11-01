package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiNativeClient
import com.example.data.NetworkMonitor
import com.example.data.PromptManager
import com.example.data.SecurePreferences
import com.example.data.model.ChatMessage
import com.example.data.model.MediaAttachment
import com.example.data.model.PromptItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab {
    WEB_STUDIO,
    NATIVE_PLAYGROUND,
    PROMPT_LIBRARY
}

data class AIStudioUiState(
    val currentUrl: String = SecurePreferences.DEFAULT_URL,
    val pageTitle: String = "Google AI Studio",
    val progress: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isOnline: Boolean = true,
    val isDesktopMode: Boolean = false,
    val isAmoledTheme: Boolean = false,
    val selectedModel: String = "gemini-2.5-flash",
    val activeTab: AppTab = AppTab.WEB_STUDIO,
    val geminiApiKey: String = "",
    val showSettingsDialog: Boolean = false,
    val showClearDataDialog: Boolean = false,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isGeneratingNative: Boolean = false,
    val savedPrompts: List<PromptItem> = emptyList(),
    val pendingAttachments: List<MediaAttachment> = emptyList(),
    val pendingPromptToInject: String? = null
)

class AIStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val securePreferences = SecurePreferences(application)
    private val networkMonitor = NetworkMonitor(application)
    private val promptManager = PromptManager(application)
    private val geminiClient = GeminiNativeClient()

    private val _uiState = MutableStateFlow(
        AIStudioUiState(
            currentUrl = securePreferences.getStartUrl(),
            isDesktopMode = securePreferences.isDesktopMode(),
            isAmoledTheme = securePreferences.isAmoledTheme(),
            selectedModel = securePreferences.getSelectedModel(),
            geminiApiKey = securePreferences.getApiKey(),
            chatMessages = promptManager.getChatHistory(),
            savedPrompts = promptManager.getPrompts()
        )
    )
    val uiState: StateFlow<AIStudioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    fun switchTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update {
            it.copy(
                progress = progress,
                isLoading = progress in 1..99
            )
        }
    }

    fun onPageStarted(url: String) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                isLoading = true,
                errorMessage = null
            )
        }
    }

    fun onPageFinished(url: String, title: String?) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                pageTitle = if (!title.isNullOrBlank()) title else "Google AI Studio",
                isLoading = false,
                isRefreshing = false
            )
        }
    }

    fun onErrorReceived(description: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = description
            )
        }
    }

    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update {
            it.copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun setRefreshing(isRefreshing: Boolean) {
        _uiState.update { it.copy(isRefreshing = isRefreshing) }
    }

    fun toggleDesktopMode(): Boolean {
        val newMode = !_uiState.value.isDesktopMode
        securePreferences.setDesktopMode(newMode)
        _uiState.update {
            it.copy(
                isDesktopMode = newMode,
                snackbarMessage = if (newMode) "Desktop mode enabled" else "Mobile mode enabled"
            )
        }
        return newMode
    }

    fun toggleAmoledTheme(): Boolean {
        val newMode = !_uiState.value.isAmoledTheme
        securePreferences.setAmoledTheme(newMode)
        _uiState.update {
            it.copy(
                isAmoledTheme = newMode,
                snackbarMessage = if (newMode) "AMOLED Pure Black enabled" else "Standard theme enabled"
            )
        }
        return newMode
    }

    fun setSelectedModel(model: String) {
        securePreferences.setSelectedModel(model)
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun saveApiKey(key: String) {
        securePreferences.saveApiKey(key)
        _uiState.update {
            it.copy(
                geminiApiKey = key.trim(),
                snackbarMessage = "API key saved securely"
            )
        }
    }

    fun clearApiKey() {
        securePreferences.clearApiKey()
        _uiState.update {
            it.copy(
                geminiApiKey = "",
                snackbarMessage = "API key cleared"
            )
        }
    }

    // Multimodal attachments for Native Chat
    fun addAttachment(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                var fileName = "attachment"

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    if (bytes.size > 15 * 1024 * 1024) {
                        _uiState.update { it.copy(snackbarMessage = "File exceeds 15MB limit") }
                        return@launch
                    }

                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val attachment = MediaAttachment(
                        uriString = uri.toString(),
                        mimeType = mimeType,
                        fileName = fileName,
                        base64Data = base64
                    )
                    _uiState.update {
                        it.copy(
                            pendingAttachments = it.pendingAttachments + attachment,
                            snackbarMessage = "Attached $fileName"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to load attachment") }
            }
        }
    }

    fun removeAttachment(uriString: String) {
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments.filterNot { att -> att.uriString == uriString })
        }
    }

    fun clearAttachments() {
        _uiState.update { it.copy(pendingAttachments = emptyList()) }
    }

    // Native Playground chat actions
    fun sendNativeChatMessage(prompt: String, systemInstruction: String) {
        val attachments = _uiState.value.pendingAttachments
        val userMsg = ChatMessage(
            text = prompt,
            isUser = true,
            attachments = attachments
        )
        val updatedHistory = _uiState.value.chatMessages + userMsg

        _uiState.update {
            it.copy(
                chatMessages = updatedHistory,
                pendingAttachments = emptyList(),
                isGeneratingNative = true
            )
        }
        promptManager.saveChatHistory(updatedHistory)

        viewModelScope.launch {
            val result = geminiClient.generateContent(
                apiKey = _uiState.value.geminiApiKey,
                prompt = prompt,
                systemInstruction = systemInstruction.ifBlank { null },
                history = updatedHistory,
                attachments = attachments,
                model = _uiState.value.selectedModel
            )

            result.fold(
                onSuccess = { reply ->
                    val modelMsg = ChatMessage(text = reply, isUser = false)
                    val newHistory = _uiState.value.chatMessages + modelMsg
                    _uiState.update {
                        it.copy(
                            chatMessages = newHistory,
                            isGeneratingNative = false
                        )
                    }
                    promptManager.saveChatHistory(newHistory)
                },
                onFailure = { err ->
                    val errorMsg = ChatMessage(
                        text = "Error: ${err.message ?: "Failed to generate reply"}",
                        isUser = false,
                        isError = true
                    )
                    val newHistory = _uiState.value.chatMessages + errorMsg
                    _uiState.update {
                        it.copy(
                            chatMessages = newHistory,
                            isGeneratingNative = false
                        )
                    }
                    promptManager.saveChatHistory(newHistory)
                }
            )
        }
    }

    fun clearNativeChat() {
        promptManager.clearChatHistory()
        _uiState.update {
            it.copy(
                chatMessages = emptyList(),
                snackbarMessage = "Chat history cleared"
            )
        }
    }

    // Local Prompt Library actions
    fun savePrompt(prompt: PromptItem) {
        promptManager.savePrompt(prompt)
        _uiState.update {
            it.copy(savedPrompts = promptManager.getPrompts())
        }
    }

    fun deletePrompt(id: String) {
        promptManager.deletePrompt(id)
        _uiState.update {
            it.copy(
                savedPrompts = promptManager.getPrompts(),
                snackbarMessage = "Prompt deleted"
            )
        }
    }

    // Web-to-Native History Sync
    fun syncWebHistory(titles: List<String>) {
        val count = promptManager.mergeSyncedPrompts(titles)
        _uiState.update {
            it.copy(
                savedPrompts = promptManager.getPrompts(),
                snackbarMessage = if (count > 0) "Synced $count prompts from Web Studio" else "No new prompts found to sync"
            )
        }
    }

    // Deep JavaScript injection into Web Studio
    fun injectPromptIntoWeb(prompt: PromptItem) {
        _uiState.update {
            it.copy(
                pendingPromptToInject = prompt.content,
                activeTab = AppTab.WEB_STUDIO
            )
        }
    }

    fun clearPendingPromptToInject() {
        _uiState.update { it.copy(pendingPromptToInject = null) }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun showClearDataDialog(show: Boolean) {
        _uiState.update { it.copy(showClearDataDialog = show) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }
}
