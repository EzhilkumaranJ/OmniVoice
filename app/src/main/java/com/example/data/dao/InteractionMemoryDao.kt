package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.InteractionMemory
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionMemoryDao {

    @Query("SELECT * FROM interaction_memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<InteractionMemory>>

    @Query("SELECT * FROM interaction_memories ORDER BY timestamp DESC")
    suspend fun getAllMemoriesList(): List<InteractionMemory>

    @Query("SELECT * FROM interaction_memories WHERE category = :category ORDER BY timestamp DESC")
    fun getMemoriesByCategory(category: String): Flow<List<InteractionMemory>>

    @Query("SELECT * FROM interaction_memories WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: Long): InteractionMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: InteractionMemory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<InteractionMemory>)

    @Update
    suspend fun updateMemory(memory: InteractionMemory)

    @Query("DELETE FROM interaction_memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM interaction_memories")
    suspend fun clearAll()

    @Query("UPDATE interaction_memories SET isCompleted = :completed WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, completed: Boolean)

    @Query("UPDATE interaction_memories SET syncedToGoogleCalendar = 1 WHERE id = :id")
    suspend fun markCalendarSynced(id: Long)

    @Query("UPDATE interaction_memories SET syncedToGoogleTasks = 1 WHERE id = :id")
    suspend fun markTasksSynced(id: Long)
}
