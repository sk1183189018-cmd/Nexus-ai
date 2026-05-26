package com.example.data.local

import androidx.room.*
import com.example.data.model.AutomationWorkflow
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface NexusDao {

    // Chat History
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()

    // memory Entries
    @Query("SELECT * FROM memory_entries ORDER BY timestamp DESC")
    fun getAllMemoryFlow(): Flow<List<MemoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(entry: MemoryEntry): Long

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("SELECT * FROM memory_entries WHERE contentKey LIKE '%' || :query || '%' OR contentValue LIKE '%' || :query || '%'")
    suspend fun searchMemory(query: String): List<MemoryEntry>

    // Automation Workflows
    @Query("SELECT * FROM automation_workflows ORDER BY id DESC")
    fun getAllWorkflowsFlow(): Flow<List<AutomationWorkflow>>

    @Query("SELECT * FROM automation_workflows WHERE isActive = 1")
    fun getActiveWorkflowsFlow(): Flow<List<AutomationWorkflow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: AutomationWorkflow): Long

    @Query("DELETE FROM automation_workflows WHERE id = :id")
    suspend fun deleteWorkflowById(id: Long)

    @Update
    suspend fun updateWorkflow(workflow: AutomationWorkflow)
}
