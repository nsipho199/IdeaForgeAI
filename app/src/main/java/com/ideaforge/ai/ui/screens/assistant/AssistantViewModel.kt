package com.ideaforge.ai.ui.screens.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ideaforge.ai.core.constants.AppConstants
import com.ideaforge.ai.core.di.PreferencesManager
import com.ideaforge.ai.core.network.ApiService
import com.ideaforge.ai.core.network.ChatCompletionRequest
import com.ideaforge.ai.core.network.ChatMessage
import com.ideaforge.ai.domain.model.AssistantMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val chatHistory = mutableListOf<ChatMessage>()

    init {
        _messages.value = listOf(
            AssistantMessage(
                id = UUID.randomUUID().toString(),
                content = "Hello! I'm your AI Assistant powered by Gemini. I can help you:\n\n" +
                    "\u2022 Improve your app idea prompts\n" +
                    "\u2022 Suggest features for your app\n" +
                    "\u2022 Answer development questions\n\n" +
                    "How can I help you today?",
                isFromUser = false
            )
        )
    }

    fun sendMessage(message: String) {
        val userMessage = AssistantMessage(id = UUID.randomUUID().toString(), content = message, isFromUser = true)
        _messages.value = _messages.value + userMessage
        _isTyping.value = true
        _isOffline.value = false

        chatHistory.add(ChatMessage(role = "user", content = message))

        viewModelScope.launch {
            try {
                val systemMsg = ChatMessage(role = "user", content = "You are IdeaForge AI assistant. Help users with Android app ideas, features, and development. Be concise and helpful.\n\nUser: $message")
                val request = ChatCompletionRequest(
                    model = AppConstants.MODEL_ID,
                    messages = listOf(systemMsg),
                    max_tokens = 1000,
                    temperature = 0.7
                )
                val apiKey = preferencesManager.getOpenCodeApiKey()
                val response = apiService.chatCompletion(
                    authorization = "Bearer $apiKey",
                    request = request
                )

                if (response.isSuccessful) {
                    val content = response.body()?.choices?.firstOrNull()?.message?.content
                    if (content != null) {
                        _messages.value = _messages.value + AssistantMessage(id = UUID.randomUUID().toString(), content = content, isFromUser = false)
                    } else {
                        _messages.value = _messages.value + AssistantMessage(id = UUID.randomUUID().toString(), content = getLocalResponse(message), isFromUser = false)
                    }
                } else {
                    _messages.value = _messages.value + AssistantMessage(id = UUID.randomUUID().toString(), content = getLocalResponse(message), isFromUser = false)
                }
            } catch (e: Exception) {
                _isOffline.value = true
                _messages.value = _messages.value + AssistantMessage(id = UUID.randomUUID().toString(), content = getLocalResponse(message), isFromUser = false)
            }
            _isTyping.value = false
        }
    }

    private fun getLocalResponse(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("improve") || lower.contains("better") -> "Tips to improve your app idea:\n\n1. Be specific about the target audience\n2. List the key features\n3. Describe the visual style\n4. Include use cases"
            lower.contains("feature") || lower.contains("suggest") -> "Popular features:\n\u2022 Push notifications\n\u2022 Dark mode\n\u2022 Offline functionality\n\u2022 Data export/import\n\u2022 Search and filtering\n\u2022 Charts and statistics"
            lower.contains("fix") || lower.contains("error") -> "Common solutions:\n\n1. Make your idea clear and detailed\n2. Add more specifics\n3. Describe the layout you want\n4. Keep your app focused"
            lower.contains("how") || lower.contains("what") -> "IdeaForge AI:\n1. You describe your app idea\n2. Gemini AI generates the code\n3. Open it in AndroidIDE to build the APK"
            lower.contains("thank") -> "You're welcome!"
            else -> "I can help you refine app ideas, suggest features, or explain the build process. What would you like to know?"
        }
    }

    fun clearChat() {
        chatHistory.clear()
        _messages.value = listOf(AssistantMessage(id = UUID.randomUUID().toString(), content = "Chat cleared. How can I help you?", isFromUser = false))
    }
}
