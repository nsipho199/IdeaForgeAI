package com.ideaforge.ai.core.network

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 16000,
    val temperature: Double = 0.7
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList()
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    val finish_reason: String? = null
)

@Serializable
data class AssistantChatRequest(
    val message: String,
    val conversationId: String? = null
)

@Serializable
data class AssistantChatResponse(
    val response: String,
    val conversationId: String? = null,
    val suggestions: List<String> = emptyList()
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String? = null
)
