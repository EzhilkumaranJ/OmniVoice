package com.example.service

import com.example.data.model.AssistantAnalysis
import com.example.data.model.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class LlmAnalysisService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    suspend fun analyzeVoiceInteraction(
        text: String,
        provider: LlmProvider,
        apiKey: String,
        selectedModel: String = provider.defaultModel
    ): AssistantAnalysis = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext offlineRuleBasedCategorization(text)
        }

        try {
            when (provider) {
                LlmProvider.GEMINI -> callGeminiApi(text, apiKey, selectedModel)
                LlmProvider.OPENAI -> callOpenAiCompatible(
                    endpoint = "https://api.openai.com/v1/chat/completions",
                    apiKey = apiKey,
                    model = selectedModel.ifBlank { "gpt-4o-mini" },
                    text = text,
                    authHeaderPrefix = "Bearer "
                )
                LlmProvider.CLAUDE -> callClaudeApi(text, apiKey, selectedModel)
                LlmProvider.GROK -> callOpenAiCompatible(
                    endpoint = "https://api.x.ai/v1/chat/completions",
                    apiKey = apiKey,
                    model = selectedModel.ifBlank { "grok-beta" },
                    text = text,
                    authHeaderPrefix = "Bearer "
                )
            }
        } catch (e: Exception) {
            // Fail gracefully to local offline categorization
            val fallback = offlineRuleBasedCategorization(text)
            fallback.copy(
                actionSuggestion = "Processed offline (LLM: ${e.message?.take(30)}...)"
            )
        }
    }

    private fun callGeminiApi(text: String, apiKey: String, model: String): AssistantAnalysis {
        val prompt = buildAnalysisPrompt(text)
        val targetModel = if (model.isBlank()) "gemini-1.5-flash" else model
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Gemini HTTP ${response.code}: ${response.message}")
            }
            val resText = response.body?.string() ?: throw RuntimeException("Empty Gemini response")
            val root = JSONObject(resText)
            val candidates = root.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val output = parts?.optJSONObject(0)?.optString("text") ?: ""

            return parseJsonAnalysis(output, text)
        }
    }

    private fun callOpenAiCompatible(
        endpoint: String,
        apiKey: String,
        model: String,
        text: String,
        authHeaderPrefix: String
    ): AssistantAnalysis {
        val prompt = buildAnalysisPrompt(text)
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "You are an intelligent personal interaction classifier and assistant. Respond only with valid JSON.")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.2)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "$authHeaderPrefix$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("LLM HTTP ${response.code}: ${response.message}")
            }
            val resText = response.body?.string() ?: throw RuntimeException("Empty response")
            val root = JSONObject(resText)
            val choices = root.optJSONArray("choices")
            val msg = choices?.optJSONObject(0)?.optJSONObject("message")
            val content = msg?.optString("content") ?: ""

            return parseJsonAnalysis(content, text)
        }
    }

    private fun callClaudeApi(text: String, apiKey: String, model: String): AssistantAnalysis {
        val prompt = buildAnalysisPrompt(text)
        val targetModel = if (model.isBlank()) "claude-3-5-sonnet-20241022" else model
        val url = "https://api.anthropic.com/v1/messages"

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val jsonBody = JSONObject().apply {
            put("model", targetModel)
            put("max_tokens", 512)
            put("messages", messages)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Claude HTTP ${response.code}: ${response.message}")
            }
            val resText = response.body?.string() ?: throw RuntimeException("Empty response")
            val root = JSONObject(resText)
            val contentArr = root.optJSONArray("content")
            val textBlock = contentArr?.optJSONObject(0)?.optString("text") ?: ""

            return parseJsonAnalysis(textBlock, text)
        }
    }

    private fun buildAnalysisPrompt(text: String): String {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.US).format(Date())
        return """
Analyze this user voice interaction captured at $nowStr:
"$text"

Respond ONLY with a JSON object in this exact schema without markdown backticks:
{
  "summary": "Concise 1-sentence headline of the interaction or action item",
  "category": "TASK" or "REMINDER" or "NOTE" or "INSIGHT",
  "dueDateIsoOrDesc": "ISO date like 2026-10-07T10:00 or empty if none",
  "recurringRule": "DAILY" or "WEEKLY" or "MONTHLY" or null,
  "tags": ["tag1", "tag2"],
  "actionSuggestion": "Actionable scheduling advice, e.g., 'Schedule on Google Calendar tomorrow at 10 AM'"
}
""".trimIndent()
    }

    private fun parseJsonAnalysis(rawOutput: String, originalText: String): AssistantAnalysis {
        try {
            val clean = rawOutput.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(clean)
            val summary = json.optString("summary", originalText.take(50))
            val category = json.optString("category", "NOTE").uppercase()
            val dueDate = json.optString("dueDateIsoOrDesc", "").takeIf { it.isNotBlank() }
            val recurring = json.optString("recurringRule", "").takeIf { it.isNotBlank() && it != "null" }
            val tagsArr = json.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    tags.add(tagsArr.getString(i))
                }
            }
            val suggestion = json.optString("actionSuggestion", null)

            return AssistantAnalysis(
                summary = summary,
                category = if (category in listOf("TASK", "REMINDER", "NOTE", "INSIGHT")) category else "NOTE",
                dueDateIsoOrDesc = dueDate,
                recurringRule = recurring,
                tags = tags,
                actionSuggestion = suggestion
            )
        } catch (_: Exception) {
            return offlineRuleBasedCategorization(originalText)
        }
    }

    /**
     * Reliable 100% offline rule-based natural language parser when offline or without API key.
     */
    fun offlineRuleBasedCategorization(text: String): AssistantAnalysis {
        val lower = text.lowercase()

        // Check category
        val category = when {
            lower.contains("remind me") || lower.contains("reminder") || lower.contains("at 7") || lower.contains("at 8") || lower.contains("tomorrow at") || lower.contains("every day") -> "REMINDER"
            lower.contains("todo") || lower.contains("to do") || lower.contains("task") || lower.contains("buy") || lower.contains("finish") || lower.contains("send") || lower.contains("prepare") -> "TASK"
            lower.contains("idea") || lower.contains("realized") || lower.contains("learned") || lower.contains("insight") -> "INSIGHT"
            else -> "NOTE"
        }

        // Recurring rule detection
        val recurring = when {
            lower.contains("every day") || lower.contains("daily") -> "DAILY"
            lower.contains("every week") || lower.contains("weekly") -> "WEEKLY"
            lower.contains("every month") || lower.contains("monthly") -> "MONTHLY"
            else -> null
        }

        // Tags
        val tags = mutableListOf<String>()
        if (lower.contains("meeting") || lower.contains("call")) tags.add("Work")
        if (lower.contains("buy") || lower.contains("groceries") || lower.contains("shop")) tags.add("Shopping")
        if (lower.contains("health") || lower.contains("doctor") || lower.contains("workout") || lower.contains("gym")) tags.add("Health")
        if (lower.contains("family") || lower.contains("home")) tags.add("Personal")
        if (tags.isEmpty()) tags.add("General")

        val suggestion = when (category) {
            "REMINDER" -> if (recurring != null) "Recurring reminder ready for Google Calendar ($recurring)" else "Ready to add to Google Calendar"
            "TASK" -> "Ready to push to Google Tasks"
            else -> "Saved to local vector DB"
        }

        return AssistantAnalysis(
            summary = text.trim().take(60),
            category = category,
            dueDateIsoOrDesc = if (category == "REMINDER" || category == "TASK") "Upcoming" else null,
            recurringRule = recurring,
            tags = tags,
            actionSuggestion = suggestion
        )
    }
}
