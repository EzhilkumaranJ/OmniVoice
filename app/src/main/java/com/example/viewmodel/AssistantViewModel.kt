package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.db.AppDatabase
import com.example.data.model.AssistantAnalysis
import com.example.data.model.InteractionMemory
import com.example.data.model.LlmProvider
import com.example.data.model.SearchResult
import com.example.data.repository.MemoryRepository
import com.example.security.CryptoBackupManager
import com.example.service.GoogleSyncManager
import com.example.service.LlmAnalysisService
import com.example.service.SpeechRecognitionManager
import com.example.service.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AssistantUiState(
    val selectedCategoryFilter: String = "ALL", // "ALL", "TASK", "REMINDER", "NOTE", "INSIGHT"
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val semanticSearchResults: List<SearchResult> = emptyList(),
    val isAnalyzingLlm: Boolean = false,
    val currentLlmProvider: LlmProvider = LlmProvider.GEMINI,
    val apiKey: String = "",
    val customModel: String = "",
    val latestAnalysis: AssistantAnalysis? = null,
    val pendingVoiceConfirmation: String? = null,
    val notificationMessage: String? = null,
    val isDarkTheme: Boolean = true,
    val activeTab: Int = 0 // 0: Feed, 1: Semantic Search, 2: Tasks/Reminders, 3: Settings/Backup
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = MemoryRepository(db.interactionMemoryDao())
    val speechManager = SpeechRecognitionManager(application)
    private val llmService = LlmAnalysisService()

    private val prefs = application.getSharedPreferences("omnivoice_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    // Database flow
    val allMemories: StateFlow<List<InteractionMemory>> = repository.allMemories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredMemories: StateFlow<List<InteractionMemory>> = combine(
        allMemories,
        _uiState
    ) { memories, state ->
        if (state.selectedCategoryFilter == "ALL") {
            memories
        } else {
            memories.filter { it.category.equals(state.selectedCategoryFilter, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Load stored settings or BuildConfig defaults
        val savedProvider = prefs.getString("llm_provider", LlmProvider.GEMINI.name) ?: LlmProvider.GEMINI.name
        val defaultApiKey = try {
            val key = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            key ?: ""
        } catch (_: Exception) {
            ""
        }
        val savedKey = prefs.getString("llm_api_key", defaultApiKey) ?: defaultApiKey
        val savedModel = prefs.getString("llm_model", "") ?: ""

        val provider = try {
            LlmProvider.valueOf(savedProvider)
        } catch (_: Exception) {
            LlmProvider.GEMINI
        }

        val savedDarkTheme = prefs.getBoolean("is_dark_theme", true)

        _uiState.value = _uiState.value.copy(
            currentLlmProvider = provider,
            apiKey = savedKey,
            customModel = savedModel,
            isDarkTheme = savedDarkTheme
        )

        // Seed initial friendly interactions if database is empty for immediate rich demo
        viewModelScope.launch {
            val existing = repository.getAllMemoriesForBackup()
            if (existing.isEmpty()) {
                seedInitialData()
            }
        }

        // Listen for speech results
        viewModelScope.launch {
            speechManager.voiceState.collect { state ->
                when (state) {
                    is VoiceState.Success -> {
                        processRecognizedVoice(state.recognizedText)
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun seedInitialData() {
        val now = System.currentTimeMillis()
        repository.insertMemory(
            rawText = "Remind me to review the quarterly roadmap and sync with Sarah every Monday at 10 AM",
            summary = "Weekly roadmap review with Sarah",
            category = "REMINDER",
            dueDate = now + 86400000 * 2,
            recurringPattern = "WEEKLY",
            tags = listOf("Work", "Roadmap")
        )
        repository.insertMemory(
            rawText = "Prepare the encryption backup architecture document and publish the APK build",
            summary = "Prepare encryption backup architecture document",
            category = "TASK",
            dueDate = now + 86400000,
            recurringPattern = null,
            tags = listOf("Dev", "Crypto")
        )
        repository.insertMemory(
            rawText = "Interesting insight: local vector embeddings using n-gram hashing allow offline sub-millisecond semantic search without requiring 500MB neural weights.",
            summary = "Local vector embeddings efficiency insight",
            category = "INSIGHT",
            dueDate = null,
            recurringPattern = null,
            tags = listOf("AI", "VectorDB")
        )
    }

    fun startListening() {
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    fun processRecognizedVoice(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzingLlm = true, pendingVoiceConfirmation = text)
            val currentState = _uiState.value
            val analysis = llmService.analyzeVoiceInteraction(
                text = text,
                provider = currentState.currentLlmProvider,
                apiKey = currentState.apiKey,
                selectedModel = currentState.customModel.ifBlank { currentState.currentLlmProvider.defaultModel }
            )

            // Auto commit to local Vector DB
            val dueDate = when {
                analysis.dueDateIsoOrDesc != null && analysis.dueDateIsoOrDesc.contains("T") -> {
                    try {
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(analysis.dueDateIsoOrDesc)?.time
                    } catch (_: Exception) {
                        System.currentTimeMillis() + 86400000
                    }
                }
                analysis.category == "REMINDER" || analysis.category == "TASK" -> System.currentTimeMillis() + 86400000
                else -> null
            }

            val newId = repository.insertMemory(
                rawText = text,
                summary = analysis.summary,
                category = analysis.category,
                dueDate = dueDate,
                recurringPattern = analysis.recurringRule,
                tags = analysis.tags
            )

            _uiState.value = _uiState.value.copy(
                isAnalyzingLlm = false,
                latestAnalysis = analysis,
                notificationMessage = "Stored in local Vector DB as ${analysis.category} (#$newId)"
            )
        }
    }

    fun manualAddInteraction(text: String) {
        processRecognizedVoice(text)
    }

    fun performSemanticSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, isSearching = true)
        viewModelScope.launch {
            val results = repository.searchSemantically(query)
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                semanticSearchResults = results
            )
        }
    }

    fun setCategoryFilter(filter: String) {
        _uiState.value = _uiState.value.copy(selectedCategoryFilter = filter)
    }

    fun setActiveTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun toggleTaskComplete(memory: InteractionMemory) {
        viewModelScope.launch {
            repository.setTaskCompleted(memory.id, !memory.isCompleted)
        }
    }

    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
            _uiState.value = _uiState.value.copy(notificationMessage = "Memory removed from local vector DB")
        }
    }

    fun syncToCalendar(context: Context, memory: InteractionMemory) {
        val success = GoogleSyncManager.syncToGoogleCalendar(context, memory)
        if (success) {
            viewModelScope.launch {
                repository.markCalendarSynced(memory.id)
                _uiState.value = _uiState.value.copy(notificationMessage = "Opened Google Calendar sync")
            }
        }
    }

    fun syncToTasks(context: Context, memory: InteractionMemory) {
        val success = GoogleSyncManager.syncToGoogleTasks(context, memory)
        if (success) {
            viewModelScope.launch {
                repository.markTasksSynced(memory.id)
                _uiState.value = _uiState.value.copy(notificationMessage = "Dispatched to Google Tasks")
            }
        }
    }

    fun updateLlmConfig(provider: LlmProvider, apiKey: String, model: String) {
        prefs.edit()
            .putString("llm_provider", provider.name)
            .putString("llm_api_key", apiKey.trim())
            .putString("llm_model", model.trim())
            .apply()

        _uiState.value = _uiState.value.copy(
            currentLlmProvider = provider,
            apiKey = apiKey.trim(),
            customModel = model.trim(),
            notificationMessage = "Saved ${provider.displayName} configuration"
        )
    }

    /**
     * Creates an encrypted backup file using AES-256-GCM.
     */
    fun createEncryptedBackup(passphrase: String): String {
        return try {
            val memories = allMemories.value
            val jsonArray = JSONArray()
            for (m in memories) {
                val obj = JSONObject().apply {
                    put("id", m.id)
                    put("rawText", m.rawText)
                    put("summary", m.summary)
                    put("category", m.category)
                    put("embeddingCsv", m.embeddingCsv)
                    put("timestamp", m.timestamp)
                    put("extractedDueDate", m.extractedDueDate ?: -1L)
                    put("recurringPattern", m.recurringPattern ?: "")
                    put("syncedToGoogleCalendar", m.syncedToGoogleCalendar)
                    put("syncedToGoogleTasks", m.syncedToGoogleTasks)
                    put("isCompleted", m.isCompleted)
                    put("tagsCsv", m.tagsCsv)
                }
                jsonArray.put(obj)
            }

            val payload = jsonArray.toString()
            val encrypted = CryptoBackupManager.encrypt(payload, passphrase)

            // Save to files directory
            val backupFile = File(getApplication<Application>().filesDir, "omnivoice_backup_${System.currentTimeMillis()}.enc")
            backupFile.writeText(encrypted)

            _uiState.value = _uiState.value.copy(notificationMessage = "Encrypted backup saved: ${backupFile.name}")
            backupFile.absolutePath
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(notificationMessage = "Backup failed: ${e.message}")
            ""
        }
    }

    /**
     * Decrypts and restores memories from an encrypted backup string or file.
     */
    fun restoreEncryptedBackup(encryptedBase64: String, passphrase: String): Boolean {
        return try {
            val decryptedJson = CryptoBackupManager.decrypt(encryptedBase64.trim(), passphrase)
            val jsonArray = JSONArray(decryptedJson)
            val list = mutableListOf<InteractionMemory>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val dueDate = obj.optLong("extractedDueDate", -1L).takeIf { it > 0 }
                val rec = obj.optString("recurringPattern", "").takeIf { it.isNotBlank() }
                list.add(
                    InteractionMemory(
                        id = 0, // Auto-generate new IDs on restore
                        rawText = obj.getString("rawText"),
                        summary = obj.getString("summary"),
                        category = obj.getString("category"),
                        embeddingCsv = obj.getString("embeddingCsv"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        extractedDueDate = dueDate,
                        recurringPattern = rec,
                        syncedToGoogleCalendar = obj.optBoolean("syncedToGoogleCalendar", false),
                        syncedToGoogleTasks = obj.optBoolean("syncedToGoogleTasks", false),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        tagsCsv = obj.optString("tagsCsv", "")
                    )
                )
            }

            viewModelScope.launch {
                repository.restoreMemories(list)
                _uiState.value = _uiState.value.copy(notificationMessage = "Successfully restored ${list.size} memories!")
            }
            true
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(notificationMessage = "Restore error: Invalid passphrase or corrupted backup")
            false
        }
    }

    fun toggleTheme() {
        val newDark = !_uiState.value.isDarkTheme
        prefs.edit().putBoolean("is_dark_theme", newDark).apply()
        _uiState.value = _uiState.value.copy(isDarkTheme = newDark)
    }

    fun dismissNotification() {
        _uiState.value = _uiState.value.copy(notificationMessage = null)
    }
}
