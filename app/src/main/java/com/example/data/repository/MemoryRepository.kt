package com.example.data.repository

import com.example.data.dao.InteractionMemoryDao
import com.example.data.model.InteractionMemory
import com.example.data.model.SearchResult
import com.example.vector.LocalVectorDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MemoryRepository(private val dao: InteractionMemoryDao) {

    val allMemories: Flow<List<InteractionMemory>> = dao.getAllMemories()

    fun getMemoriesByCategory(category: String): Flow<List<InteractionMemory>> =
        dao.getMemoriesByCategory(category)

    suspend fun insertMemory(
        rawText: String,
        summary: String,
        category: String,
        dueDate: Long? = null,
        recurringPattern: String? = null,
        tags: List<String> = emptyList()
    ): Long = withContext(Dispatchers.Default) {
        // Compute embedding locally using vector engine
        val textToEmbed = "$summary $rawText ${tags.joinToString(" ")}"
        val embedding = LocalVectorDb.computeEmbedding(textToEmbed)
        val embeddingCsv = LocalVectorDb.vectorToString(embedding)

        val memory = InteractionMemory(
            rawText = rawText,
            summary = summary,
            category = category,
            embeddingCsv = embeddingCsv,
            extractedDueDate = dueDate,
            recurringPattern = recurringPattern,
            tagsCsv = tags.joinToString(",")
        )

        dao.insertMemory(memory)
    }

    suspend fun searchSemantically(query: String, threshold: Float = 0.15f): List<SearchResult> =
        withContext(Dispatchers.Default) {
            if (query.isBlank()) return@withContext emptyList()

            val queryVector = LocalVectorDb.computeEmbedding(query)
            val allList = dao.getAllMemoriesList()

            val scoredList = allList.mapNotNull { memory ->
                val memVector = LocalVectorDb.stringToVector(memory.embeddingCsv)
                val score = LocalVectorDb.cosineSimilarity(queryVector, memVector)
                // Filter and sort by semantic relevance
                if (score >= threshold) {
                    SearchResult(memory = memory, similarityScore = score)
                } else null
            }.sortedByDescending { it.similarityScore }

            scoredList
        }

    suspend fun setTaskCompleted(id: Long, completed: Boolean) {
        dao.setTaskCompleted(id, completed)
    }

    suspend fun markCalendarSynced(id: Long) {
        dao.markCalendarSynced(id)
    }

    suspend fun markTasksSynced(id: Long) {
        dao.markTasksSynced(id)
    }

    suspend fun deleteMemory(id: Long) {
        dao.deleteMemory(id)
    }

    suspend fun getAllMemoriesForBackup(): List<InteractionMemory> = dao.getAllMemoriesList()

    suspend fun restoreMemories(memories: List<InteractionMemory>) = dao.insertAll(memories)

    suspend fun clearAll() = dao.clearAll()
}
