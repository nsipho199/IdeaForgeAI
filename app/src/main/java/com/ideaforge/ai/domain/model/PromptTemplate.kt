package com.ideaforge.ai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PromptTemplate(
    val id: String,
    val title: String,
    val description: String,
    val prompt: String,
    val category: PromptCategory,
    val icon: String
)

@Serializable
enum class PromptCategory {
    BUSINESS, EDUCATION, CHURCH, FINANCE, SHOPPING,
    HEALTH, GAMES, AI, PRODUCTIVITY, UTILITIES,
    SOCIAL_MEDIA, GOVERNMENT
}
