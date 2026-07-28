package com.ideaforge.ai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AssistantMessage(
    val id: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: AssistantMessageType = AssistantMessageType.TEXT
)

@Serializable
enum class AssistantMessageType {
    TEXT,
    IMPROVE_PROMPT,
    EXPLAIN_PROJECT,
    SUGGEST_FEATURES,
    FIX_ISSUES
}
