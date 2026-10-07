package com.example.data.model

data class SearchResult(
    val memory: InteractionMemory,
    val similarityScore: Float
)

enum class LlmProvider(val displayName: String, val defaultModel: String) {
    GEMINI("Google Gemini", "gemini-1.5-flash"),
    OPENAI("OpenAI (ChatGPT)", "gpt-4o-mini"),
    CLAUDE("Anthropic Claude", "claude-3-5-sonnet-20241022"),
    GROK("xAI Grok", "grok-beta")
}

data class AssistantAnalysis(
    val summary: String,
    val category: String, // "NOTE", "TASK", "REMINDER"
    val dueDateIsoOrDesc: String? = null,
    val recurringRule: String? = null,
    val tags: List<String> = emptyList(),
    val actionSuggestion: String? = null
)
